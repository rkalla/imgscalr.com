package jobs;

import java.io.File;
import java.io.FileFilter;

import play.Logger;
import play.Play;
import play.jobs.Every;
import play.jobs.Job;

/**
 * Play caches all uploads as temporary files in the /tmp folder and never
 * cleans them up. Instead we periodically clean them up manually so we don't
 * run out of disk space on the server.
 */
@Every("1h")
@SuppressWarnings("rawtypes")
public class TmpDirCleanupJob extends Job {
	private static final long LAST_MODIFIED_THRESHOLD = 3600000; // 1hr
	private static final FileFilter FILE_FILTER = new TempFileNameFilter();

	@Override
	public void doJob() throws Exception {
		super.doJob();

		// SANITY-CHECK, make sure the temp dir exists and we can use it.
		if (Play.tmpDir == null || Play.readOnlyTmp) {
			Logger.warn(
					"Play Temp Dir [%s] is either Missing or Read-ONLY, Job will exit...",
					(Play.tmpDir == null ? "null" : Play.tmpDir
							.getAbsolutePath()));
			return;
		}

		// Get a list of all the tmp files we can safely delete.
		File[] tempFiles = Play.tmpDir.listFiles(FILE_FILTER);

		if (tempFiles != null && tempFiles.length > 0) {
			int totalErased = tempFiles.length;
			Logger.info("Temp Cleanup, erasing %s files...", tempFiles.length);

			for (File file : tempFiles) {
				if (!file.delete()) {
					totalErased--;
					Logger.error("\tUnable to Erase Temp File [%s]",
							file.getAbsolutePath());
				}
			}

			Logger.info("Temp Cleanup Complete [%s out of %s erased]",
					totalErased, tempFiles.length);
		}
	}

	static class TempFileNameFilter implements FileFilter {
		/**
		 * Used to generally determine if this is one of Play's HTTP POST
		 * temporary upload cache files which have the format characteristics
		 * of: 36 chars long (Java's UUID class), no file extension and lastly
		 * it includes the '-' character at positions 8, 13, 18 and 23 which is
		 * the format of a UUID string.
		 */
		@Override
		public boolean accept(File pathname) {
			boolean accept = false;

			/*
			 * Make sure there is a file, that it isn't a directory and that it
			 * was last modified AT LEAST a specific threshold of time ago (to
			 * ensure we aren't erasing files that are being processed/uploaded
			 * right now).
			 */
			if (pathname != null
					&& !pathname.isDirectory()
					&& (System.currentTimeMillis() - pathname.lastModified() > LAST_MODIFIED_THRESHOLD)) {
				String filename = pathname.getName();

				/*
				 * Check the format of the filename to ensure it is a UUID-style
				 * filename so we can safely erase it.
				 */
				accept = (filename != null && filename.length() == 36
						&& filename.charAt(8) == '-'
						&& filename.charAt(13) == '-'
						&& filename.charAt(18) == '-'
						&& filename.charAt(23) == '-' && filename.indexOf('.',
						28) == -1);
			}

			return accept;
		}
	}
}