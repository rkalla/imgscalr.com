package controllers;

public interface Constants {
	/**
	 * Size of the buffer used to process uploaded files as they are
	 * Base64-decoded to images on disk.
	 */
	public static final int FILE_BUFFER_SIZE = 65536; // 64k

	/**
	 * Length of the unique file keys generated and used to name the uploads on
	 * the CDN.
	 */
	public static final int UNIQUE_FILE_KEY_SIZE = 9;

	public static final int SIZE_THUMBNAIL = 150;
	public static final int SIZE_SMALL = 250;
	public static final int SIZE_MEDIUM = 500;
	public static final int SIZE_LARGE = 1024;
	public static final int SIZE_XLARGE = 1280;
	public static final int SIZE_XXLARGE = 1600;
	public static final int SIZE_XXXLARGE = 1920;

	public static final String SUFFIX_THUMBNAIL = "T";
	public static final String SUFFIX_SMALL = "S";
	public static final String SUFFIX_MEDIUM = "M";
	public static final String SUFFIX_LARGE = "L";
	public static final String SUFFIX_XLARGE = "XL";
	public static final String SUFFIX_XXLARGE = "XXL";
	public static final String SUFFIX_XXXLARGE = "XXXL";

	public static final String S3_BUCKET_NAME = "i.imgscalr.com";
	public static final String S3_BASE_URL = "http://" + S3_BUCKET_NAME + '/';
}