/*
 * This camera service extends android service and SurfaceHolder
 * It will click photos when this service will be invoked
 * 
 * @author Aditi Kulkarni
 */
package com.camera.project.camerawithoutpreview;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

public class CameraService extends Service  implements SurfaceHolder.Callback {

	public Camera mCamera;
	public Parameters camParameters;
	public Bitmap bmp,bitmap;
	public FileOutputStream fos;
	public String FLASH_MODE;
	public int QUALITY_MODE = 0;
	public boolean isFrontCamRequest = false;
	public Camera.Size camSize;
	public SurfaceView surfaceView;
	public SurfaceHolder sHolder;
	public WindowManager windowManager;
	WindowManager.LayoutParams layoutParams;
	public Intent cameraIntent;
	public SharedPreferences pref;
	public Editor editor;
	int width = 0, height = 0;
	/*
	 * (non-Javadoc)
	 * @see android.app.Service#onCreate()
	 * This method will be called when the activity is first created.
	 * @param 
	 * @return void
	 */
	@Override
	public void onCreate() {
	    super.onCreate();
	}
	
	/*
	 * Following method create instance for front facing camera
	 * 
	 * @param 
	 * @return android.hardware.Camera
	 */
	private Camera openFrontFacingCamera()
	{
		if(mCamera != null)
		{
			mCamera.stopPreview();
	        mCamera.release();
		}
		
		int noOfCamera = 0;
		Camera cam = null;
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		noOfCamera = Camera.getNumberOfCameras();
		for(int i=0;i< noOfCamera;i++)
		{
			Camera.getCameraInfo(i, cameraInfo);
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
			{
				try {
	                cam = Camera.open(i);
	            } catch (RuntimeException e) {
	                Log.e("CameraService:openFrontFacingCamera()",
	                        "Camera failed to open: " + e.getLocalizedMessage());
	                /*
	                 * Toast.makeText(getApplicationContext(),
	                 * "Front Camera failed to open", Toast.LENGTH_LONG)
	                 * .show();
	                 */
	            }
			}
		}
		return cam;
	}
	
	/*
	 * This method set resolutions for picture
	 * @param
	 * @return void
	 */
	private void setBestPictureResolution()
	{
		width = pref.getInt("Picture_Width", 0);
	    height = pref.getInt("Picture_height", 0);
	    
	    if(width == 0 || height == 0)
	    {
	    	camSize = getBiggestPicture(camParameters);
	    	if(camSize != null)
	    	{
	    		camParameters.setPictureSize(camSize.width, camSize.height);
	    	}
	    	width = camSize.width;
	    	height = camSize.height;
	    	editor.putInt("Photo_Width",width);
	    	editor.putInt("Photo_Height",height);
	    	editor.commit();
	    }
	    else
	    {
	    	camParameters.setPictureSize(width, height);
	    }
	    
	}
	
	/*
	 * This returns best suitable size configuration for available camera
	 * 
	 * @param android.hardware.Camera.Parameters
	 * @return android.hardware.Camera.Size
	 */
	private Camera.Size getBiggestPicture(Camera.Parameters parameters)
	{
		Camera.Size cSize = null;
		
		for(Camera.Size size: parameters.getSupportedPictureSizes())
		{
			if(cSize == null)
			{
				cSize = size;
			}
			else
			{
				int cSizeArea = cSize.width * cSize.height;
				int newArea = size.width * size.height;
				
				if(newArea > cSizeArea)
				{
					cSize = size;
				}
			}
		}
		return cSize;
	}
	
	/*
	 * Following method will check if device has camera or not
	 * 
	 * @param android.content.Context
	 * @return boolean
	 */
	private boolean checkCameraHardware(Context context)
	{
		if(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
		{
			return true;
		}
		else 
			return false;
	}
	
	/*
	 * Check if this device has front camera
	 * 
	 * @param android.content.Context
	 * @return boolean
	 */	
	private boolean checkFrontCamera(Context context) {
	    if (context.getPackageManager().hasSystemFeature(
	            PackageManager.FEATURE_CAMERA_FRONT)) {
	        // this device has front camera
	        return true;
	    } else {
	        // no front camera on this device
	        return false;
	    }
	}
	
	Handler handler = new Handler();
	
	/*
	 * Asynchronous task to take photo in background
	 */
	private class TakePhoto extends AsyncTask<Intent, Void, Void>{

		@Override
		protected Void doInBackground(Intent... params) {
			// TODO Auto-generated method stub
			takePhoto(params[0]);
			return null;
		}
		
	}
	
	/*
	 * Following method will take photo 
	 * 
	 * @param android.content.Intent
	 * @return void
	 */
	private synchronized void takePhoto(Intent intent)
	{
		if(checkCameraHardware(getApplicationContext()))
		{
			Bundle extras = intent.getExtras();
			if(extras != null)
			{
				FLASH_MODE = extras.getString("FLASH");
				isFrontCamRequest = extras.getBoolean("Front_Request");
				QUALITY_MODE = extras.getInt("Quality_Mode");
			}
			
			if(isFrontCamRequest)
			{
				FLASH_MODE = "off";
				mCamera = openFrontFacingCamera();
				if(mCamera != null)
				{
					try{
						mCamera.setPreviewDisplay(sHolder);
					}
					catch(IOException ioe)
					{
						handler.post(new Runnable() {

                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(),
                                        "API dosen't support front camera",
                                        Toast.LENGTH_LONG).show();
                            }
                        });

                        stopSelf();
					}
					Camera.Parameters parameters = mCamera.getParameters();
					camSize = getBiggestPicture(parameters);
					if(camSize != null)
					{
						parameters.setPictureSize(camSize.width, camSize.height);
					}
					// set camera parameters
                    mCamera.setParameters(parameters);
                    mCamera.startPreview();
                    mCamera.takePicture(null, null, mCall);
				}
				else
				{
					mCamera = null;
                    handler.post(new Runnable() {

                        @Override
                        public void run() {
                            Toast.makeText(
                                    getApplicationContext(),
                                    "Your Device dosen't have Front Camera !",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

                    stopSelf();
					
				}
			}
			else
			{
				if (mCamera != null) {
	                mCamera.stopPreview();
	                mCamera.release();
	                mCamera = Camera.open();
	            } else
	                mCamera = getCameraInstance();
				
				try
				{
					if(mCamera != null)
					{
						mCamera.setPreviewDisplay(surfaceView.getHolder());
						camParameters = mCamera.getParameters();
						if (FLASH_MODE == null || FLASH_MODE.isEmpty()) {
	                        FLASH_MODE = "off";
	                    }
						camParameters.setFlashMode(FLASH_MODE);
						setBestPictureResolution();
						
						Log.d("Qaulity", camParameters.getJpegQuality() + "");
	                    Log.d("Format", camParameters.getPictureFormat() + "");
	                    
	                 // set camera parameters
	                    mCamera.setParameters(camParameters);
	                    mCamera.startPreview();
	                    Log.d("ImageTakin", "OnTake()");
	                    mCamera.takePicture(null, null, mCall);
					}
					else
					{
						handler.post(new Runnable() {

	                        @Override
	                        public void run() {
	                            Toast.makeText(getApplicationContext(),
	                                    "Camera is unavailable !",
	                                    Toast.LENGTH_LONG).show();
	                        }
	                    });
					}
				}
				catch(IOException ioe)
				{
					 Log.e("TAG", "CmaraHeadService()::takePicture", ioe);
				}
			}
		}
		else
		{
			 handler.post(new Runnable() {

		            @Override
		            public void run() {
		                Toast.makeText(getApplicationContext(),
		                        "Your Device dosen't have a Camera !",
		                        Toast.LENGTH_LONG).show();
		            }
		        });
		        stopSelf();
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
	 */
	@SuppressWarnings("deprecation")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		cameraIntent = intent;
		pref = getApplicationContext().getSharedPreferences("MyPref", 0);
		editor = pref.edit();
		
		windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		
		layoutParams = new WindowManager.LayoutParams(
	            WindowManager.LayoutParams.WRAP_CONTENT,
	            WindowManager.LayoutParams.WRAP_CONTENT,
	            WindowManager.LayoutParams.TYPE_PHONE,
	            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
	            PixelFormat.TRANSLUCENT);

		layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
		layoutParams.width = 1;
		layoutParams.height = 1;
		layoutParams.x = 0;
		layoutParams.y = 0;
		
		surfaceView = new SurfaceView(getApplicationContext());
		
		windowManager.addView(surfaceView, layoutParams);
		
		sHolder = surfaceView.getHolder();
		sHolder.addCallback(this);
		
		// tells Android that this surface will have its data constantly
	    // replaced
	    if (Build.VERSION.SDK_INT < 11)
	        sHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	    return 1;		
	}
	
	Camera.PictureCallback mCall = new Camera.PictureCallback() {
		
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			// TODO Auto-generated method stub
			
			// decode the data obtained by the camera into a Bitmap
	        Log.d("ImageTakin", "Done");
	        if (bmp != null)
	            bmp.recycle();
	        if(bitmap != null)
	        	bitmap.recycle();
	        System.gc();
	        bmp = decodeBitmap(data);
	        bitmap = decodeBitmap(data);
	        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	        if (bmp != null && QUALITY_MODE == 0)
	            bmp.compress(Bitmap.CompressFormat.JPEG, 70, bytes);
	        else if (bmp != null && QUALITY_MODE != 0)
	            bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY_MODE, bytes);

	        File imagesFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MYGALLERY");
	        //File imagesFolder = new File(Environment.getExternalStorageDirectory(), "MYGALLERY");
	        if (!imagesFolder.exists())
	            imagesFolder.mkdirs(); // <----
	        File image = new File(imagesFolder, System.currentTimeMillis()
	                + ".jpg");
	        

	        // write the bytes in file
	        try {
	            fos = new FileOutputStream(image);
	        } catch (FileNotFoundException e) {
	            Log.e("TAG", "FileNotFoundException", e);
	            // TODO Auto-generated catch block
	        }
	        try {
	            fos.write(bytes.toByteArray());
	            
	        } catch (IOException e) {
	            Log.e("TAG", "fo.write::PictureTaken", e);
	            // TODO Auto-generated catch block
	        }

	        // remember close de FileOutput
	        try {
	            fos.close();
	            if (Build.VERSION.SDK_INT < 19)
	                sendBroadcast(new Intent(
	                        Intent.ACTION_MEDIA_MOUNTED,
	                        Uri.parse("file://"
	                                + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))));
	            else {
	                MediaScannerConnection
	                        .scanFile(
	                                getApplicationContext(),
	                                new String[] { image.toString() },
	                                null,
	                                new MediaScannerConnection.OnScanCompletedListener() {
	                                    public void onScanCompleted(
	                                            String path, Uri uri) {
	                                        Log.i("ExternalStorage", "Scanned "
	                                                + path + ":");
	                                        Log.i("ExternalStorage", "-> uri="
	                                                + uri);
	                                    }
	                                });
	            }

	        } catch (IOException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	        }
	        if (mCamera != null) {
	            mCamera.stopPreview();
	            mCamera.release();
	            mCamera = null;
	        }
	        /*
	         * Toast.makeText(getApplicationContext(),
	         * "Your Picture has been taken !", Toast.LENGTH_LONG).show();
	         */
	        Log.d("Camera", "Image Taken !");
	        
	        if (bmp != null) {
	        	bmp.recycle();
	            bmp = null;
	            System.gc();
	        }
	        mCamera = null;
	        handler.post(new Runnable() {

	            @Override
	            public void run() {
	                Toast.makeText(getApplicationContext(),
	                        "Your Picture has been taken !", Toast.LENGTH_SHORT)
	                        .show();
	                
	                try {
	        			SendPhoto(bitmap);
	        		} catch (Exception e) {
	        			// TODO Auto-generated catch block
	        			e.printStackTrace();
	        		}
	            }
	        });
	        stopSelf();
	        
		}
	};
	
	/*
	 * Create android.hardware.Camera instance
	 * 
	 * @param
	 * @return android.hardware.Camera
	 */
	public static Camera getCameraInstance() {
	    Camera c = null;
	    try {
	        c = Camera.open(); // attempt to get a Camera instance
	    } catch (Exception e) {
	        // Camera is not available (in use or does not exist)
	    }
	    return c; // returns null if camera is unavailable
	}
	
	/*
	 * (non-Javadoc)
	 * @see android.view.SurfaceHolder.Callback#surfaceChanged(android.view.SurfaceHolder, int, int, int)
	 */
	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
		
	}

	/*
	 * (non-Javadoc)
	 * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.SurfaceHolder)
	 */
	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		// TODO Auto-generated method stub
		if (cameraIntent != null)
	        new TakePhoto().execute(cameraIntent);
		
	}

	/*
	 * (non-Javadoc)
	 * @see android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view.SurfaceHolder)
	 */
	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		// TODO Auto-generated method stub
		if (mCamera != null) {
	        mCamera.stopPreview();
	        mCamera.release();
	        mCamera = null;
	    }
	}

	/*
	 * Decode byte data to bitmap
	 * 
	 * @param byte[] byte array
	 * @return android.graphics.Bitmap
	 */
	public static Bitmap decodeBitmap(byte[] data) {

	    Bitmap bitmap = null;
	    BitmapFactory.Options bfOptions = new BitmapFactory.Options();
	    bfOptions.inDither = false; // Disable Dithering mode
	    bfOptions.inPurgeable = true; // Tell to gc that whether it needs free
	                                    // memory, the Bitmap can be cleared
	    bfOptions.inInputShareable = true; // Which kind of reference will be
	                                        // used to recover the Bitmap data
	                                        // after being clear, when it will
	                                        // be used in the future
	    bfOptions.inTempStorage = new byte[32 * 1024];

	    if (data != null)
	        bitmap = BitmapFactory.decodeByteArray(data, 0, data.length,
	                bfOptions);
	    return bitmap;
	}
	/*
	 * (non-Javadoc)
	 * @see android.app.Service#onBind(android.content.Intent)
	 */
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	/*
	 * (non-Javadoc)
	 * @see android.app.Service#onDestroy()
	 * This method will stop preview and release camera object
	 */
	@Override
	public void onDestroy() {
	    if (mCamera != null) {
	        mCamera.stopPreview();
	        mCamera.release();
	        mCamera = null;
	    }
	    if (surfaceView != null)
	        windowManager.removeView(surfaceView);
	    Intent intent = new Intent("custom-event-name");
	    // You can also include some extra data.
	    intent.putExtra("message", "This is my message!");
	    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

	    super.onDestroy();
	}

	/*
	 * This method will call Async task to upload photo
	 */
	public static void SendPhoto(Bitmap bitmap) throws Exception
	{
		new UploadPhoato().execute(bitmap);
	}
	
	private static class UploadPhoato extends AsyncTask<Bitmap, Void, Void>
	{
		protected Void doInBackground(Bitmap... bitmaps) {
			
			if(bitmaps[0] == null)
				return null;
			
			Bitmap bitmap = bitmaps[0];
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteStream); // Convert bitmap to byteoutputstream
			String encodedImage = Base64.encodeToString(byteStream.toByteArray(), Base64.DEFAULT); // Change code
			ArrayList<NameValuePair> dataToSend = new ArrayList<NameValuePair>();//Change code
			dataToSend.add(new BasicNameValuePair("Name",System.currentTimeMillis() + ".jpg")); //Change code
			dataToSend.add(new BasicNameValuePair("Image", encodedImage)); //Change code
			
			DefaultHttpClient httpClient = new DefaultHttpClient();
			try
			{
				HttpPost httpPost = new HttpPost("http://192.168.78.128/UploadImage.php");//Original Code
				httpPost.setEntity(new UrlEncodedFormEntity(dataToSend));
				
				Log.d("CameraService:Async Request","request " + httpPost.getRequestLine());
				
				HttpResponse response = null;
				
				try
				{
					response = httpClient.execute(httpPost);
				}
				catch (ClientProtocolException e) {
				
					Log.e("CameraService:AsyncTask",e.getMessage());
				} catch (IOException e) {
					Log.e("CameraService:AsyncTask",e.getMessage());
				}
				try {
					if (response != null)
						Log.d("CameraService:Asynctask", "response " + response.getStatusLine().toString());
						Log.d("CameraService:AsyncTask","Adit Response ++++"+EntityUtils.toString(response.getEntity()));
						
				} 
				catch(Exception exp)
				{
					Log.e("CameraService:AsyncTask",exp.getMessage());
				}
			}
			catch(Exception e)
			{
				Log.e("CameraService:AsyncTak", e.getMessage());
			}
						
			if (byteStream != null) {
				try {
					byteStream.close();
				} catch (IOException e) {
					Log.e("CameraService:AsyncTask",e.getMessage());
				}
			}
			return null;
		}
		
		/*
		 * 
		 */
		@Override
		protected void onProgressUpdate(Void... values) {
			
			super.onProgressUpdate(values);
		}
		
		/*
		 * This method will execute when server side code is does with execution
		 */
		@Override
		protected void onPostExecute(Void result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			Log.d("CameraService:onPostExecute","Your image is uploaded successfully");
		}
	}
}
