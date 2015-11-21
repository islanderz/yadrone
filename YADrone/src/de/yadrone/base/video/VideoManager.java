/*
 *
  Copyright (c) <2011>, <Shigeo Yoshida>
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
The names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.yadrone.base.video;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;

import de.yadrone.base.exception.IExceptionListener;
import de.yadrone.base.exception.VideoException;
import de.yadrone.base.manager.AbstractTCPManager;
import de.yadrone.base.utils.ARDroneUtils;
import sa.uavcs.paho.client.MavMqttClient;

public class VideoManager extends AbstractTCPManager implements ImageListener {
	private IExceptionListener excListener;

	private VideoDecoder decoder;

	private boolean skip;

	private ArrayList<ImageListener> listener = new ArrayList<ImageListener>();

	public VideoManager(InetAddress inetaddr, VideoDecoder decoder, IExceptionListener excListener) {
		super(inetaddr);
		this.decoder = decoder;
		this.excListener = excListener;
	}

	public void addImageListener(ImageListener listener) {
		this.listener.add(listener);
		if (this.listener.size() == 1)
			decoder.setImageListener(this);
	}

	public void removeImageListener(ImageListener listener) {
		this.listener.remove(listener);
		if (this.listener.size() == 0)
			decoder.setImageListener(null);
	}

	private int countImages = 0;
	private int countPublishedImages = 0;
	private long lastSec = 0;
	
	/** Called only by decoder to inform all the other listener */
	public void imageUpdated(BufferedImage image) {

		for (int i = 0; i < listener.size(); i++) {
			countImages++;
			// KNS: Comment or uncomment to view video in decoder
			listener.get(i).imageUpdated(image);
			System.err.println("Count + " + countImages);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				// KNS: Previsouly jpg
				ImageIO.write(image, "jpg", baos);

				byte[] imageInByte = baos.toByteArray();
//				System.err.println("Size of baos" + baos.size());

				// Execute every ms 
				 
				// 48 Images per min Maximum 
				// 45 Images per min - at Landa=1000,  26 lost frame
				// 44 Images per min - at Landa=1500,  3 frame lost
				
			    long sec = System.currentTimeMillis() / 1500;
			    if (sec != lastSec) {
			    	countPublishedImages++;
			    	System.out.println("Published image count: " + countPublishedImages);
			       //code to run
			       MavMqttClient.getInstance().publishsync("image/imagestream", 0, imageInByte);
			       lastSec = sec;
			    }//If():
			 
				
				// baos.flush();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		}
	}

	public boolean connect(int port) throws IOException {
		if (decoder == null)
			return false;

		return super.connect(port);
	}

	public void reinitialize() {
		System.out.println("VideoManager: reinitialize video stream ...");
		close();
		System.out.println("VideoManager: previous stream closed ...");
		try {
			System.out.println("VideoManager: create new decoder");
			decoder.stop();
			decoder = (VideoDecoder) decoder.getClass().newInstance();
			decoder.setImageListener(this);

			Thread.sleep(1000);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("VideoManager: start connecting again ...");
		new Thread(this).start();
	}

	@Override
	public void run() {
		if (decoder == null)
			return;
		try {
			System.out.println("VideoManager: connect ");
			connect(ARDroneUtils.VIDEO_PORT);

			System.out.println("VideoManager: tickle ");
			ticklePort(ARDroneUtils.VIDEO_PORT);

			// manager.setVideoBitrateControl(VideoBitRateMode.DISABLED); //
			// bitrate set to maximum

			System.out.println("VideoManager: decode ");
			decoder.decode(getInputStream());
		} catch (Exception exc) {
			exc.printStackTrace();
			excListener.exeptionOccurred(new VideoException(exc));
		}

		close();
	}

	@Override
	public void close() {
		if (decoder == null)
			return;

		super.close();
	}

}
