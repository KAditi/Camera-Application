package com.camera.project.camerawithoutpreview;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;

import android.util.Log;

public class Multipart implements HttpEntity{

	private String boundary = null;
	ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
	boolean isSetLast = false;
	boolean isSetFirst = false;
	
	public Multipart()
	{
		this.boundary = System.currentTimeMillis()+"";
	}
	
	public void writeFirstBoundary()
	{
		if(!isSetFirst)
		{
			try
			{
				byteOutStream.write(("--" + boundary + "\r\n").getBytes());
			}
			catch(IOException ioe)
			{
				Log.e("Multipart:writeFirstBoundary",ioe.getMessage());
			}
		}
		isSetFirst = true;
	}
	
	public void writeLastBoundary()
	{
		if(isSetLast){
			return;
		}
		try
		{
			byteOutStream.write(("\r\n--" + boundary + "--\r\n").getBytes());
		}
		catch(IOException ioe)
		{
			Log.e("Multipart:writeLastBoundary", ioe.getMessage());
		}
		
		isSetLast = true;
	}
	
	public void addPart(final String key, final String value)
	{
		writeFirstBoundary();
		try
		{
			byteOutStream.write(("Content-Disposition: form-data; name=\"" +key+"\"\r\n").getBytes());
			byteOutStream.write("Content-Type: text/plain; charset=UTF-8\r\n".getBytes());
			byteOutStream.write("Content-Transfer-Encoding: 8bit\r\n\r\n".getBytes());
			byteOutStream.write(value.getBytes());
			byteOutStream.write(("\r\n--" + boundary + "\r\n").getBytes());
		}
		catch(IOException ioe)
		{
			Log.e("Multipart:addPart",ioe.getMessage());
		}
	}
	
	public void addPart(final String key, final String fileName, final InputStream fin){
        //addPart(key, fileName, fin, "application/octet-stream");//Working code part
		addPart(key, fileName, fin, "multipart/form-data");//Change to test image
    }
	
	public void addPart(final String key, final String fileName, final InputStream fin, String type)
	{
		writeFirstBoundary();
		try
		{
			type = "Content-Type: "+type+"\r\n";
			//byteOutStream.write(("Content-Disposition: form-data; name=\""+ key+"\"; filename=\"" + fileName + "\"\r\n").getBytes());//Working code part
			byteOutStream.write(("Content-Disposition: form-data; name=\""+ key+"\"; filename=\"" + fileName + "\"\r\n").getBytes());
			byteOutStream.write(type.getBytes());
			byteOutStream.write("Content-Transfer-Encoding: binary\r\n\r\n".getBytes());
			
			final byte[] tempArray = new byte[4096];
			int i = 0;
			while((i = fin.read(tempArray))!= -1)
			{
				byteOutStream.write(tempArray, 0, 1);
			}
			
			byteOutStream.flush();
		}
		catch(IOException ioe)
		{
			Log.e("Multipart:addPart()",ioe.getMessage());
		}
		finally
		{
			try
			{
				fin.close();
			}
			catch(IOException ioe)
			{
				Log.e("Multipart:addPart()",ioe.getMessage());
			}
		}
		
		
	}
	
	public void addPart(String key, File value)
	{
		try 
		{
            addPart(key, value.getName(), new FileInputStream(value));
        } 
		catch(FileNotFoundException ioe)
		{
			Log.e("Multipart:addPart()", ioe.getMessage());
		}
		
	}
	
	@Override
	public void consumeContent() throws IOException,UnsupportedOperationException {
		// TODO Auto-generated method stub
		if (isStreaming()) {
            throw new UnsupportedOperationException(
            "Streaming entity does not implement #consumeContent()");
        }
		
	}

	@Override
	public InputStream getContent() throws IOException, IllegalStateException,UnsupportedOperationException {
		return new ByteArrayInputStream(byteOutStream.toByteArray());
	}

	@Override
	public Header getContentEncoding() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getContentLength() {
		writeLastBoundary();
        return byteOutStream.toByteArray().length;
	}

	@Override
	public Header getContentType() {
		return new BasicHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
	}

	@Override
	public boolean isChunked() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isRepeatable() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isStreaming() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void writeTo(OutputStream outStream) throws IOException {
		outStream.write(byteOutStream.toByteArray());
		
	}

}
