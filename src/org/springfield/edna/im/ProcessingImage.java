package org.springfield.edna.im;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageOutputStream;

public class ProcessingImage {
	
	public BufferedImage workingImage = null;
	public ImageWriteParam iwparam = new JPEGImageWriteParam(Locale.getDefault());
	public String recompress = null;

	public ProcessingImage(File inputfile) {
		try {
			workingImage = ImageIO.read(inputfile);
		} catch (IOException e) {
			System.out.println("Could not read input image :"+inputfile.getAbsolutePath());
		}
	}
	
	public void writeToFile(String filename) {
		System.out.println("Write to file : "+filename);
		String tmpfilename = filename;
		
		if (recompress!=null) tmpfilename +="_temp"; // we need to recompress
		
		File dest = new File(tmpfilename);
		try {
			Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpeg");
			ImageWriter writer = iter.next(); 
			FileImageOutputStream output = new FileImageOutputStream(dest);
			writer.setOutput(output);
			IIOImage image = new IIOImage(workingImage, null, null);
			writer.write(null, image, iwparam);
			writer.dispose();
		} catch (Exception e) {
			System.out.println("Could not write to compressed output image "+filename);
		}
		
		// ok so we now have it on filesystem, was recompressed asked ?
		if (recompress!=null) {
			doRecompress(tmpfilename,filename,recompress);
			dest.delete(); // delete the _temp file
		}
	}
	
	private void doRecompress(String inputname,String outputname,String options) {
		String cmd = "/springfield/edna/bin/jpeg-recompress";
		cmd += " -a";
		cmd += " -m "+options;
		cmd += " "+inputname;
		cmd += " "+outputname;

		try {
			Process child = Runtime.getRuntime().exec(cmd);
			InputStream is = child.getErrorStream();
			if (is != null) {
				BufferedReader br = new BufferedReader( new InputStreamReader(is) );
				String line;
				while ((line = br.readLine()) != null) {
					
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	
}