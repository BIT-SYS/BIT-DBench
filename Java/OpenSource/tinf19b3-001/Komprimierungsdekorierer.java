package de.dhbw.tinf19b3.pattern.decorator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class Komprimierungsdekorierer extends Datenübertragungsdekorierer {
	
	public Komprimierungsdekorierer(
			Datenübertragung nachfolger) {
		super(nachfolger);
	}
	
	@Override
	public void sende(
			String nachricht) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
	        GZIPOutputStream gzip = new GZIPOutputStream(out);
		    gzip.write(nachricht.getBytes("utf-8"));
		    gzip.close();
		    String komprimiert = out.toString("utf-8");
		    super.sende(komprimiert);
		} catch (IOException e) {
			return;
		}
	}
}
