package notifiers;

import play.mvc.Mailer;
import controllers.response.UploadResponse;

public class UploadMailer extends Mailer {
	public static void upload(String ipAddress, UploadResponse response) {
		setSubject("[imgscalr] Image Uploaded %s", response.original.url);
		addRecipient("riyad@thebuzzmedia.com");
		setFrom("imgscalr.com <admin@thebuzzmedia.com>");
		
		send(ipAddress, response);
	}
}