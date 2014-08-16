package org.magnum.dataup;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.magnum.dataup.model.VideoStatus.VideoState;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * This simple VideoController allows clients to send HTTP POST requests with
 * videos that are stored in memory using a list. Clients can send HTTP GET
 * requests to receive a JSON listing of the videos that have been sent to the
 * controller so far. Stopping the controller will cause it to lose the history
 * of videos that have been sent to it because they are stored in memory.
 * 
 * Notice how much simpler this VideoController is than the original
 * VideoServlet? Spring allows us to dramatically simplify our service. Another
 * important aspect of this version is that we have defined a VideoSvcApi that
 * provides strong typing on both the client and service interface to ensure
 * that we don't send the wrong paraemters, etc.
 * 
 * @author jules
 *
 */

// Tell Spring that this class is a Controller that should
// handle certain HTTP requests for the DispatcherServlet
@Controller
public class VideoController {

	// An in-memory list that the servlet uses to store the
	// videos that are sent to it by clients
	public static final String VIDEO_SVC_PATH = "/video";
	public static final String DATA_PARAMETER = "data";
	public static final String ID_PARAMETER = "id";
	public static final String VIDEO_DATA_PATH = VIDEO_SVC_PATH + "/{id}/data";

	private VideoFileManager videoDataMgr;

	private Map<Long, Video> videos = new HashMap<Long, Video>();
	private static final AtomicLong currentId = new AtomicLong(0L);

	// private Map<Long,Video> videos = new HashMap<Long, Video>();

	public Video save(Video entity) {
		checkAndSetId(entity);
		return entity;
	}

	private void checkAndSetId(Video entity) {
		if (entity.getId() == 0) {
			entity.setId(currentId.incrementAndGet());
		}
	}

	@RequestMapping(value = "/video/{id}/data", method = RequestMethod.GET)
	public void getData(@PathVariable("id") long id, HttpServletResponse response) {
		try {
			videoDataMgr = VideoFileManager.get();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		Video requested = null;
		if (videos.containsKey(id)) {
			requested = videos.get(id);
		}
		
		if (requested == null) {
			response.setStatus(404);
		} else {
			try {
				videoDataMgr.copyVideoData(requested, response.getOutputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@RequestMapping(value = "/video/{id}/data", method = RequestMethod.POST)
	public @ResponseBody VideoStatus setVideoData(@PathVariable("id") long id,
			@RequestParam("data") MultipartFile videoData, HttpServletResponse response) {
		try {
			videoDataMgr = VideoFileManager.get();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Video requested = null;
		if (videos.containsKey(id)) {
			requested = videos.get(id);
		}

		if (requested != null) {
			try {
				videoDataMgr.saveVideoData(requested, videoData.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			response.setStatus(404);
		}
		return new VideoStatus(VideoState.READY);
	}

	@RequestMapping(value = VIDEO_SVC_PATH, method = RequestMethod.POST)
	public @ResponseBody Video addVideo(@RequestBody Video v) {
		// this.checkAndSetId(video);
		Video video = this.save(v);
		String url = this.getDataUrl(video.getId());
		video.setDataUrl(url);
		videos.put(video.getId(), video);
		return video;
	}

	// Receives GET requests to /video and returns the current
	// list of videos in memory. Spring automatically converts
	// the list of videos to JSON because of the @ResponseBody
	// annotation.
	// TODO finished
	@RequestMapping(value = VIDEO_SVC_PATH, method = RequestMethod.GET)
	public @ResponseBody Collection<Video> getVideoList() {
		return videos.values();
	}

	private String getUrlBaseForLocalServer() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
				.getRequest();
		String base = "http://" + request.getServerName()
				+ ((request.getServerPort() != 80) ? ":" + request.getServerPort() : "");
		return base;
	}

	private String getDataUrl(long videoId) {
		String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
		return url;
	}

	public void saveSomeVideo(Video v, MultipartFile videoData) throws IOException {
		videoDataMgr.saveVideoData(v, videoData.getInputStream());
	}

}