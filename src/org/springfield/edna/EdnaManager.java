package org.springfield.edna;

import java.awt.AlphaComposite;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;

import javax.imageio.ImageWriteParam;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.imgscalr.Scalr;
import org.springfield.edna.im.ProcessingImage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class EdnaManager {
	
	private enum validactions { crop,scale, adjust, rotate, transparent, compress, original,recompress; }
	private static EdnaManager instance;
	private static HashMap<String, String> scriptcommands = null;
	private int counter = 0;
	
	private EdnaManager() {
        System.out.println("Edna Manager started");
		if (scriptcommands == null) {
			scriptcommands = readCommandList();
		}
	}
	
    public static EdnaManager instance(){
    	if(instance==null) instance = new EdnaManager();
    	return instance;
    }
	
	public void sendImageBasedOnURL(String image,HttpServletRequest request,HttpServletResponse response) {
		String commands[] = null;
		

		String script = request.getParameter("script");
		if (script!=null) {
			commands = applyScript(script);
		}
		
		if(commands==null) { //Apply default command = thumbnail
			commands = applyScript("thumbnail");
		}
				
		String diskname = getOutputName(image,commands);
		File file = new File(diskname);
		if (file.exists()) {
			// send from cache !
			sendFile(file,response);
		} else {
			// generate file
			generateImageOnDisk(image,diskname,commands);
			// now send from cache 
			file = new File(diskname);	
			if (file.exists()) {
				sendFile(file,response);
			}
		}
	}	
	
	
	private void generateImageOnDisk(String inputimage,String diskname,String[] commands) {
	
		String path = "/springfield/edna/tmpimages/";
		String filename = ""+(counter++); // simple counter to make sure filenames are new each time. files are deleted when done

		boolean download = false;
		int pos = inputimage.indexOf("/external/");
		if (pos==0) {
			download = saveUrltoDisk(path+filename,"http://"+inputimage.substring(pos+10));
			if (inputimage.indexOf(".svg")!=-1) {
				System.out.println("SVG detected");
				// we should pass this untouched 
				File tmpimage = new File(path+filename); 
				String dirname = diskname.substring(0,diskname.lastIndexOf("/"));
				File dir = new File(dirname);
				if (!dir.exists()) {
					dir.mkdirs();
				}
				tmpimage.renameTo(new File(diskname));
				return;
			}
		} else {
			download = saveUrltoDisk(path+filename,"http://images1.noterik.com/"+inputimage);
			if (!download) { download = saveUrltoDisk(path+filename,"http://images2.noterik.com/"+inputimage); }
			if (!download) { download = saveUrltoDisk(path+filename,"http://images3.noterik.com/"+inputimage); }
		}
		
		if (download) {
		
			File tmpimage = new File(path+filename);
			ProcessingImage image = new ProcessingImage(tmpimage);
		
			for (int i=0;i<commands.length;i++) { // needs minimal 2 commands ?
				String[] command = commands[i].split("=");
				String key = command[0];
				String value = command[1];
				processImageNew(image, key, value);
			}	
		
			String dirname = diskname.substring(0,diskname.lastIndexOf("/"));
			File dir = new File(dirname);
			if (!dir.exists()) {
				dir.mkdirs();
			}
			
			image.writeToFile(diskname);
			
			tmpimage.delete();
		}
	}
	
	private void processImageNew(ProcessingImage image,String key, String value) {
	    switch (validactions.valueOf(key)) {
	       case crop :
        	   doCrop(image,value);
	    	   break;
           case scale :
        	   doScale(image,value);
        	   break;
           case adjust :
        	   break;
           case rotate :
        	   break;
           case transparent :
        	   break;
           case compress :
        	   doCompress(image,value);
        	   break;
           case recompress :
        	   doRecompress(image,value);
        	   break;
           case original:
        	   break;    	 
       }

	}
	
	private void doScale(ProcessingImage image,String value) {
			String[] params = value.split("x");
			try {
				int newWidth = Integer.parseInt(params[0]);
				int newHeight = Integer.parseInt(params[1]);
				image.workingImage = org.imgscalr.Scalr.resize(image.workingImage,Scalr.Method.QUALITY,Scalr.Mode.FIT_EXACT,newWidth, newHeight,Scalr.OP_ANTIALIAS);
			} catch(Exception e) {
				
			}
	}
	
	private void doCrop(ProcessingImage image,String value) {
		String[] params = value.split("x");
		try {
			String cXco = params[0];
			String cYco = params[1];
			String cWidth = params[2];
			String cHeight = params[3];
			int x = Integer.parseInt(cXco);
			int y = Integer.parseInt(cYco);
			int w = Integer.parseInt(cWidth);
			int h = Integer.parseInt(cHeight);
			image.workingImage = org.imgscalr.Scalr.crop(image.workingImage, x, y, w, h);
		} catch(Exception e) {
			
		}
}
	
	private void doCompress(ProcessingImage image,String value) {
  	  try{
			float cVal = Float.parseFloat(value);
			if (cVal < 0 || cVal > 1) {
				System.out.println("edna: compress only valid between 0 and 1");
			} else {
				image.iwparam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			    image.iwparam.setCompressionQuality(cVal);
			}
		}catch(Exception e){
		}
	}
	
	private void doRecompress(ProcessingImage image,String value) {
	  	  image.recompress = value;
	}

	

	/** parse parameters from script (xml parsing) */
	private static HashMap<String, String> readCommandList() {
		String filename = "/springfield/edna/config/cmdlist.xml";
		HashMap<String, String> cmdList = new HashMap<String, String>();

		try {
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			domFactory.setNamespaceAware(true);
			DocumentBuilder builder = domFactory.newDocumentBuilder();
			Document doc = builder.parse(new File(filename));
			doc.getDocumentElement().normalize();
			XPath xpath = XPathFactory.newInstance().newXPath();

			Object result = xpath.evaluate("//fsxml/cmdlist", doc,
					XPathConstants.NODESET);
			NodeList nodes = (NodeList) result;

			for (int i = 0; i < nodes.getLength(); i++) {
				Node cmdNode = (Node) xpath.evaluate("properties/name",
						nodes.item(i), XPathConstants.NODE);
				NodeList cmdSteps = (NodeList) xpath.evaluate("cmdstep/properties", nodes.item(i),
						XPathConstants.NODESET);
				String cmdline = "";
				for (int j = 0; j < cmdSteps.getLength(); j++) {
					Node nNode = cmdSteps.item(j);
					if (nNode.getNodeType() == Node.ELEMENT_NODE) {
						Element eElement = (Element) nNode;
						if (!cmdline.equals(""))
							cmdline += ",";
						cmdline += getTagValue("key", eElement) + "="
								+ getTagValue("value", eElement);
					}
				}
				cmdList.put(cmdNode.getTextContent(), cmdline);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return cmdList;
	}
	
	private static String getTagValue(String sTag, Element eElement) {
		NodeList nlList = eElement.getElementsByTagName(sTag).item(0).getChildNodes();
		Node nValue = nlList.item(0);
		return nValue.getNodeValue();
	}
	
	private String[] applyScript(String name) {
		String script = scriptcommands.get(name);
		if (script != null) {
				return script.split(",");
		}
		return null;
	}

	private String getOutputName(String filename, String[] commands) {

		String basedir = "/springfield/edna/outputimages";
		int pos = filename.lastIndexOf("/");
		String imagepath = filename.substring(0,pos);
		
		String cmdstring = filename.substring(pos+1);
		int pos2 = cmdstring.indexOf(".");
		String extension = cmdstring.substring(pos2);
		cmdstring = cmdstring.substring(0,pos2);
		
		for (int i=0;i<commands.length;i++) {
			cmdstring+="-"+commands[i];
		}
		
		return basedir+imagepath+"/"+cmdstring+extension;
	}
	
	private void sendFile(File file,HttpServletResponse response) {
		try {
			response.setHeader("Cache-Control", "no-transform,public,max-age=86400,s-maxage=86400");
			response.setContentType(setCorrectExtention(file.getName()));
			response.setContentLength((int)file.length());
			FileInputStream in = new FileInputStream(file);    
			OutputStream out = response.getOutputStream();
			byte[] buf = new byte[10240];    
			int i = 0;    
		    while ((i = in.read(buf)) > 0) {   
				out.write(buf, 0, i);    
			}    
			out.flush();       
			out.close(); 
			in.close();
		
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private boolean saveUrltoDisk(String filename,String url) {
		try {
			BufferedInputStream in = null;
			FileOutputStream fout = null;
			try {
				in = new BufferedInputStream(new URL(url).openStream());
				fout = new FileOutputStream(filename);

				final byte data[] = new byte[1024];
				int count;
				while ((count = in.read(data, 0, 1024)) != -1) {
					fout.write(data, 0, count);
				}
			} finally {
				if (in != null) {
					in.close();
				}
				if (fout != null) {
					fout.close();
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
			return false;
		}	
		return true;
	}
	
	private String setCorrectExtention(String name) {
		String ext = name.substring(name.lastIndexOf(".")+1);
		ext = ext.toLowerCase();
		if (ext.equals("jpg")) {
			return("image/jpeg");
		} else if (ext.equals("svg")) {
			return("image/svg+xml");
		}
		return("image/jpeg");
	}

	
}
