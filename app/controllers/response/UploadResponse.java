package controllers.response;

public class UploadResponse {
	public static final int CODE_SUCCESS = 1;
	public static final int CODE_FAILURE_GENERAL = 2;
	public static final int CODE_FAILURE_MISSING_FILENAME = 3;
	public static final int CODE_FAILURE_TMPDIR_READONLY = 4;
	public static final int CODE_FAILURE_UNSUPPORTED_FILE_TYPE = 5;
	public static final int CODE_FAILURE_CANNOT_ACCESS_TMP_DECODE_FILE = 6;
	public static final int CODE_FAILURE_DECODING_TO_TMP_FILE = 7;
	public static final int CODE_FAILURE_CANNOT_CREATE_AWS_CLIENT = 8;
	public static final int CODE_FAILURE_UNABLE_TO_GENERATE_ALT_SIZES = 9;
	public static final int CODE_FAILURE_UNABLE_TO_UPLOAD_TO_CDN = 10;

	public static enum Type {
		SUCCESS(CODE_SUCCESS, "Upload Complete"), GENERAL_FAILURE(
				CODE_FAILURE_GENERAL, "Service Temporarily Unavailable (Code: "
						+ CODE_FAILURE_GENERAL + ")"), MISSING_FILENAME(
				CODE_FAILURE_MISSING_FILENAME,
				"Your browser may not fully support HTML5, the image's filename was missing."), TEMP_DIR_READONLY(
				CODE_FAILURE_TMPDIR_READONLY,
				"Server is Unable to Process Your Upload (Code: "
						+ CODE_FAILURE_TMPDIR_READONLY + ")"), UNSUPPORTED_FILE_TYPE(
				CODE_FAILURE_UNSUPPORTED_FILE_TYPE,
				"Uploaded File Type Not Supported (sorry)"), CANNOT_ACCESS_TMP_DECODE_FILE(
				CODE_FAILURE_CANNOT_ACCESS_TMP_DECODE_FILE,
				"Error Preparing for Image Processing (Code: "
						+ CODE_FAILURE_CANNOT_ACCESS_TMP_DECODE_FILE + ")"), FAILED_DECODING_TO_TMP_FILE(
				CODE_FAILURE_DECODING_TO_TMP_FILE,
				"Error Processing Image (Code: "
						+ CODE_FAILURE_DECODING_TO_TMP_FILE + ")"), CANNOT_CREATE_AWS_CLIENT(
				CODE_FAILURE_CANNOT_CREATE_AWS_CLIENT,
				"CDN Client Cannot be Created (Code: "
						+ CODE_FAILURE_CANNOT_CREATE_AWS_CLIENT + ")"), UNABLE_TO_GENERATE_ALT_SIZES(
				CODE_FAILURE_UNABLE_TO_GENERATE_ALT_SIZES,
				"Unable to Generate Alternate Sizes (Code: "
						+ CODE_FAILURE_UNABLE_TO_GENERATE_ALT_SIZES + ")"), UNABLE_TO_UPLOAD_TO_CDN(
				CODE_FAILURE_UNABLE_TO_UPLOAD_TO_CDN,
				"Unable to upload hosted images to CDN, that's not good.");

		int code;
		String message;

		private Type(int code, String message) {
			this.code = code;
			this.message = message;
		}
	}

	public Boolean success = Boolean.FALSE;

	public Integer code = Type.GENERAL_FAILURE.code;
	public String message = Type.GENERAL_FAILURE.message;

	public String originalFileName;
	public String uniqueFileKey;
	public String uniqueFileName;

	// public String originalUrl;
	// public String thumbnailUrl;
	// public String mediumUrl;
	// public String largeUrl;

	public Image original = new Image();
	public Image thumbnail = new Image();
	public Image small = new Image();
	public Image medium = new Image();
	public Image large = new Image();
	public Image xlarge = new Image();
	public Image xxlarge = new Image();
	public Image xxxlarge = new Image();

	public UploadResponse() {
		this(Type.GENERAL_FAILURE);
	}

	public UploadResponse(Type type) {
		setType(type);
	}

	public UploadResponse setType(Type type) {
		if (type == Type.SUCCESS)
			success = true;

		this.code = type.code;
		this.message = type.message;
		
		return this;
	}

	public class Image {
		public int width;
		public int height;
		public long sizeInBytes;
		public String url;
	}
}