package suite.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import suite.util.LogUtil;
import suite.util.SocketUtil;
import suite.util.SocketUtil.Io;

public class LoadBalancer {

	private List<String> servers;
	private volatile List<String> alives;
	private AtomicInteger counter = new AtomicInteger();

	private int port = 80;

	public LoadBalancer(List<String> servers) {
		this.servers = servers;
	}

	public void run() throws IOException {
		final boolean running[] = new boolean[] { true };

		Thread probe = new Thread() {
			public void run() {
				while (running[0])
					try {
						List<String> alives1 = new ArrayList<>();

						for (String server : servers)
							try (Socket socket = new Socket(server, port)) {
								alives1.add(server);
							} catch (SocketException ex) {
							}

						alives = alives1;
						Thread.sleep(500l);
					} catch (Exception ex) {
						LogUtil.error(ex);
					}
			}
		};

		Io io = new Io() {
			public void serve(InputStream is, OutputStream os) throws IOException {
				int count = counter.getAndIncrement();
				List<String> alives0 = alives;

				String server = alives0.get(count % alives0.size());

				try (Socket socket = new Socket(server, port)) {
					InputStream sis = socket.getInputStream();
					OutputStream sos = socket.getOutputStream();

					AtomicBoolean quitter = new AtomicBoolean(false);
					new CopyStreamThread(is, sos, quitter).start();
					new CopyStreamThread(sis, os, quitter).start();
				}
			}
		};

		try {
			probe.start();
			SocketUtil.listen(port, io);
		} finally {
			running[0] = false;
		}
	}

}
