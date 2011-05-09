package controllers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import net.iharder.Base64;
import notifiers.UploadMailer;
import play.Logger;
import play.Play;
import play.mvc.Controller;
import play.mvc.Http.Header;

import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.thebuzzmedia.common.util.RandomUtils;
import com.thebuzzmedia.imgscalr.Scalr;
import com.thebuzzmedia.imgscalr.Scalr.Method;
import com.thebuzzmedia.imgscalr.Scalr.Mode;

import controllers.response.UploadResponse;

// TODO: Need to look into adding FORM auth tokens so not just anybody can POST to this controller
// Play can generate these using a script tag.
public class Upload extends Controller {
	private static Set<String> validFileExt = new HashSet<String>();
	private static Map<String, String> extMimeTypeMap = new HashMap<String, String>();

	private static AmazonS3 s3Client;

	static {
		// Init the fileExt Set
		for (String name : ImageIO.getReaderFormatNames())
			validFileExt.add(name);

		// Init the mimeType Map
		extMimeTypeMap.put("jpg", "image/jpeg");
		extMimeTypeMap.put("JPG", "image/jpeg");
		extMimeTypeMap.put("jpeg", "image/jpeg");
		extMimeTypeMap.put("JPEG", "image/jpeg");
		extMimeTypeMap.put("png", "image/png");
		extMimeTypeMap.put("PNG", "image/jpeg");
		extMimeTypeMap.put("GIF", "image/gif");
		extMimeTypeMap.put("gif", "image/gif");
		extMimeTypeMap.put("BMP", "image/bmp");
		extMimeTypeMap.put("bmp", "image/bmp");
	}

	public static void upload() {
		// Add separator to the log for easier visual parsing.
		Logger.info("================================================");

		long fileSize = 0;
		String fileName;
		String fileExtension;
		String fileType;
		final UploadResponse response = new UploadResponse();

		long elapsedTime = System.currentTimeMillis();
		long totalElapsedTime = elapsedTime;

		// Get header file information
		Header header = request.headers.get("x-file-name");
		fileName = (header == null ? null : header.value());

		/*
		 * SANITY-CHECK, if we haven't even gotten a fileName, something is
		 * wrong with this upload and we should just get out of here because at
		 * the LEAST, the fileName should have come through.
		 */
		if (fileName == null || fileName.length() == 0) {
			renderJSON(response.setType(UploadResponse.Type.MISSING_FILENAME));
		}

		// Update response with what we know so far
		response.originalFileName = fileName;

		fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1);
		header = request.headers.get("x-file-type");
		fileType = (header == null ? null : header.value());

		/*
		 * HELPING-HAND, if no file type came across from the client (most
		 * likely due to browser's HTML5 File API spec impl) we will guess one
		 * based on the file extension so we have as much information as
		 * possible later.
		 */
		if (fileType == null)
			fileType = extMimeTypeMap.get(fileExtension);

		header = request.headers.get("x-file-size");

		if (header != null) {
			try {
				fileSize = Long.parseLong(header.value());

				if (fileSize > 0) {
					/*
					 * Temporarily update the response with the size we have for
					 * the file right now. Below when we get a real file size,
					 * we will update the value again.
					 */
					response.original.sizeInBytes = fileSize;
				}
			} catch (NumberFormatException e) {
				Logger.error(
						"Unable to parse 'x-file-size' header value '%s' as an integer.",
						header.value());
			}
		}

		Logger.info("PERFORMANCE [Header Parse Time: %s ms]",
				System.currentTimeMillis() - elapsedTime);
		Logger.info(
				"UPLOAD from %s [fileName=%s, fileSize=%s, fileType=%s, fileExt=%s]",
				request.remoteAddress, fileName, fileSize, fileType,
				fileExtension);

		/*
		 * SANITY-CHECK, make sure we support processing this file type. We
		 * check by extension because it is inexpensive and not all browsers
		 * consistently implement the 'type' property for the HTML5 File API in
		 * order to get the file's MIME type.
		 */
		if (!validFileExt.contains(fileExtension)) {
			Logger.info("Unsupported File Type [extension=%s]", fileExtension);
			renderJSON(response
					.setType(UploadResponse.Type.UNSUPPORTED_FILE_TYPE));
		}

		/*
		 * SANITY-CHECK, if the tmp dir is readOnly, this application cannot
		 * work and we should blow up.
		 */
		if (Play.readOnlyTmp) {
			Logger.fatal(
					"Temp Dir [%s] READ-ONLY, imgscalr.com cannot function!",
					Play.tmpDir.getAbsolutePath());
			renderJSON(response.setType(UploadResponse.Type.TEMP_DIR_READONLY));
		}

		/*
		 * Generate a unique key (name) for this file because we have to store
		 * it along side millions of other files in the same dir on the CDN.
		 * 
		 * IMPL NOTE: We don't take the time to check if a file with the
		 * existing name is already on the CDN so we want the generated name to
		 * have the smallest possible chances of collision. Using 9 characters,
		 * that gives us 52^9 possible combinations (~2.8 quadrillion) using an
		 * upper and lowercase alphabet.
		 */
		String uniqueFileKey = new String(RandomUtils.randomChars(
				RandomUtils.UPPER_AND_LOWER_CASE_ALPHABET,
				Constants.UNIQUE_FILE_KEY_SIZE));
		String uniqueFileName = uniqueFileKey + '.' + fileExtension;

		// Update response with what we know so far
		response.uniqueFileKey = uniqueFileKey;
		response.uniqueFileName = uniqueFileName;

		// Create temporary file to decode the Base64 stream into.
		File tempFile = new File(Play.tmpDir, uniqueFileName);

		int totalBytesRead = 0;
		Base64.InputStream decodingStream = null;
		OutputStream outputStream = null;

		try {
			// Prepare the IN and OUT streams for decoding to the temp file.
			decodingStream = new Base64.InputStream(request.body, Base64.DECODE);
			outputStream = new FileOutputStream(tempFile, false);

			int bytesRead = 0;
			elapsedTime = System.currentTimeMillis();
			byte[] buffer = new byte[Constants.FILE_BUFFER_SIZE];

			// Write the contents to the temp file.
			while ((bytesRead = decodingStream.read(buffer)) > 0) {
				outputStream.write(buffer, 0, bytesRead);
				totalBytesRead += bytesRead;
			}

			// Done decoding, close streams.
			outputStream.close();
			decodingStream.close();

			/*
			 * If we didn't get the file size from the header initially, try one
			 * more time here to get it from the file we just wrote to disk.
			 */
			if (fileSize == 0)
				fileSize = tempFile.length();
		} catch (FileNotFoundException fe) {
			Logger.error(
					fe,
					"Unable to access/create the temporary file [%s] to decode the submitted image to.",
					tempFile.getAbsolutePath());
			renderJSON(response
					.setType(UploadResponse.Type.CANNOT_ACCESS_TMP_DECODE_FILE));
		} catch (IOException ie) {
			Logger.error(
					ie,
					"An exception occurred while decoding the InputStream from the client stream and writing it out to a temporary file: %s",
					tempFile.getAbsolutePath());
			renderJSON(response
					.setType(UploadResponse.Type.FAILED_DECODING_TO_TMP_FILE));
		} finally {
			try {
				if (outputStream != null)
					outputStream.close();

				if (decodingStream != null)
					decodingStream.close();

				Logger.info("PERFORMANCE [Decode Image Time: %s ms]",
						System.currentTimeMillis() - elapsedTime);
			} catch (Exception e) {
				// no-op
			}
		}

		Logger.info("Base64 Decoded to File [size=%s, tempFile=%s]",
				tempFile.length(), tempFile.getAbsolutePath());

		File thumbnailFile = null;
		File smallFile = null;
		File mediumFile = null;
		File largeFile = null;
		BufferedImage originalImage = null;

		/*
		 * For safety reasons we wrote the original to disk first (to make sure
		 * we have it) now we will read it back in and generate the other
		 * versions of it.
		 */
		try {
			originalImage = ImageIO.read(tempFile);
		} catch (IOException e) {
			Logger.error(
					e,
					"An exception occurred while trying to load the uploaded file [%s] as a BufferedImage to generate alternative sizes for it.",
					tempFile.getAbsolutePath());
			renderJSON(response
					.setType(UploadResponse.Type.UNABLE_TO_GENERATE_ALT_SIZES));
		}

		elapsedTime = System.currentTimeMillis();

		/*
		 * Now optionally generate every supported image width size that is
		 * smaller than the original. We don't want to generate any unnecessary
		 * up-scaled instances of the original.
		 */
		thumbnailFile = generateAltSize(response, uniqueFileKey, fileExtension,
				Constants.SUFFIX_THUMBNAIL, tempFile, originalImage,
				Constants.SIZE_THUMBNAIL, false);

		if (thumbnailFile != null)
			Logger.info("\tGenerated THUMBNAIL Image: %s",
					thumbnailFile.getAbsolutePath());

		smallFile = generateAltSize(response, uniqueFileKey, fileExtension,
				Constants.SUFFIX_SMALL, tempFile, originalImage,
				Constants.SIZE_SMALL, false);

		if (smallFile != null)
			Logger.info("\tGenerated SMALL Image: %s",
					smallFile.getAbsolutePath());

		mediumFile = generateAltSize(response, uniqueFileKey, fileExtension,
				Constants.SUFFIX_MEDIUM, tempFile, originalImage,
				Constants.SIZE_MEDIUM, false);

		if (mediumFile != null)
			Logger.info("\tGenerated MEDIUM Image: %s",
					mediumFile.getAbsolutePath());

		largeFile = generateAltSize(response, uniqueFileKey, fileExtension,
				Constants.SUFFIX_LARGE, tempFile, originalImage,
				Constants.SIZE_LARGE, false);

		if (largeFile != null)
			Logger.info("\tGenerated LARGE Image: %s",
					largeFile.getAbsolutePath());

		Logger.info("PERFORMANCE [Alt. Image Size Generation Time: %s ms]",
				System.currentTimeMillis() - elapsedTime);

		// Update the original image meta manually
		response.original.width = originalImage.getWidth();
		response.original.height = originalImage.getHeight();
		response.original.sizeInBytes = tempFile.length();

		elapsedTime = System.currentTimeMillis();

		// FIRST, upload the original to CDN
		response.original.url = uploadToS3(tempFile, true);

		if (response.original.url == null) {
			Logger.error("Unable to upload original image to CDN");
			renderJSON(response
					.setType(UploadResponse.Type.UNABLE_TO_UPLOAD_TO_CDN));
		}

		/*
		 * NEXT, upload all additional scaled versions to CDN. If any of these
		 * uploads fail, we don't really care because their URL will just remain
		 * null as it goes back to the client and at least the original is safe.
		 */
		response.thumbnail.url = uploadToS3(thumbnailFile, true);
		response.small.url = uploadToS3(smallFile, true);
		response.medium.url = uploadToS3(mediumFile, true);
		response.large.url = uploadToS3(largeFile, true);

		Logger.info("PERFORMANCE [S3 Upload Time: %s ms]",
				System.currentTimeMillis() - elapsedTime);
		totalElapsedTime = System.currentTimeMillis() - totalElapsedTime;
		Logger.info("TOTAL PERFORMANCE [Total Run Time: %s ms (%s seconds)]",
				totalElapsedTime, ((double) totalElapsedTime / (double) 1000));

		// If we made it this far, then it was a success.
		response.setType(UploadResponse.Type.SUCCESS);

		// Determine the IP address where the upload originated from
		Header sourceIP = request.headers.get("X-Real-IP");

		if (sourceIP == null)
			sourceIP = request.headers.get("X-Forwarded-For");

		// Send mail notice.
		UploadMailer.upload((sourceIP == null ? request.remoteAddress
				: sourceIP.value()), response);

		// Send the response back to the client.
		renderJSON(response);
	}

	private static File generateAltSize(UploadResponse response,
			String uniqueFileKey, String fileExtension, String fileSuffix,
			File sourceFile, BufferedImage sourceImage, int targetSize,
			boolean upscale) {
		File resizedFile = null;
		File parentDir = sourceFile.getParentFile();

		/*
		 * We don't want to upscale any images sent to us, so if the original
		 * image is the same size or smaller than the version we are trying to
		 * generate, just return. There is nothing to do.
		 */
		if (!upscale && sourceImage.getWidth() <= targetSize)
			return resizedFile;

		if (sourceImage.getWidth() > targetSize) {
			resizedFile = new File(parentDir, uniqueFileKey + '-' + fileSuffix
					+ '.' + fileExtension);
			BufferedImage resizedImage = Scalr.resize(sourceImage,
					Method.QUALITY, Mode.FIT_TO_WIDTH, targetSize);

			try {
				ImageIO.write(resizedImage, fileExtension, resizedFile);
			} catch (IOException e) {
				Logger.error(
						e,
						"An exception occurred while generating an alt sized image [%s] for source image %s",
						resizedFile.getAbsolutePath(),
						sourceFile.getAbsolutePath());
			}

			// Update the response data with the image info
			UploadResponse.Image imageMeta = null;

			switch (targetSize) {
			case Constants.SIZE_THUMBNAIL:
				imageMeta = response.thumbnail;
				break;

			case Constants.SIZE_SMALL:
				imageMeta = response.small;
				break;

			case Constants.SIZE_MEDIUM:
				imageMeta = response.medium;
				break;

			case Constants.SIZE_LARGE:
				imageMeta = response.large;
				break;

			case Constants.SIZE_XLARGE:
				imageMeta = response.xlarge;
				break;

			case Constants.SIZE_XXLARGE:
				imageMeta = response.xxlarge;
				break;

			case Constants.SIZE_XXXLARGE:
				imageMeta = response.xxxlarge;
				break;
			}

			if (imageMeta != null) {
				imageMeta.width = resizedImage.getWidth();
				imageMeta.height = resizedImage.getHeight();
				imageMeta.sizeInBytes = resizedFile.length();
			} else {
				Logger.error(
						"Unable to Set Image Meta on Generated Resource. No Match for Width of '%s' Found!",
						targetSize);
			}

			// Make things on the GC, explicitly flush native resources.
			if (resizedImage != null)
				resizedImage.flush();
		}

		return resizedFile;
	}

	private static String uploadToS3(File file, boolean deleteOnComplete) {
		String url = null;

		if (file != null) {
			if (s3Client == null)
				s3Client = createS3Client();

			// Check 1 more time in case the init failed.
			if (s3Client != null) {
				// Upload to S3
				PutObjectResult uploadResult = s3Client.putObject(
						Constants.S3_BUCKET_NAME, file.getName(), file);
				String eTag = (uploadResult == null ? null : uploadResult
						.getETag());

				// Confirm the upload succeeded
				if (eTag != null && eTag.length() > 1) {
					// Adjust ACL for the new file, otherwise no one can access
					// it.
					s3Client.setObjectAcl(Constants.S3_BUCKET_NAME,
							file.getName(), CannedAccessControlList.PublicRead);

					url = Constants.S3_BASE_URL + file.getName();
					Logger.info("CDN Upload Complete [remoteFile=%s]", url);

					if (deleteOnComplete) {
						if (!file.delete()) {
							Logger.error("Unable to Delete File [%s]",
									file.getAbsolutePath());
						} else
							Logger.info("Deleted Temporary File [%s]",
									file.getAbsolutePath());
					}
				}
			}
		}

		return url;
	}

	private static AmazonS3 createS3Client() {
		AmazonS3 client = null;

		try {
			client = new AmazonS3Client(new PropertiesCredentials(
					Upload.class
							.getResourceAsStream("AwsCredentials.properties")));
		} catch (Exception e) {
			Logger.error(
					e,
					"An exception occurred while trying to load AwsCredentials.properties and create an AmazonS3Client instance.");
		}

		return client;
	}
}