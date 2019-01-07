package org.elastos.app.FileDemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.elastos.carrier.filetransfer.FileTransferState;

public class MainActivity extends AppCompatActivity {
	private static final String TAG = "MainActivity";
    private TextView mFileTransferState;
	private TextView mFilePath;
	private TextView mFriendOnline;
	private TextView mFriendId;
	private TextView mShowingText;
	private ImageView mQRCodeImage;
	private ImageView mReceiveFile;
	private Handler mHandler;
	private Button mAddFriendButton;
	private Button mTransferButton;
	private static SimpleCarrier sSimpleCarrier;

	private static final int SELECTFILE_REQUEST_CODE = 0x02;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        InitView();
    }

    private void InitView() {
	    mFileTransferState = findViewById(R.id.filetransferState);
	    mFilePath = findViewById(R.id.filePath);
	    mFriendOnline = findViewById(R.id.friendOnline);
	    mFriendId = findViewById(R.id.friendId);
		mShowingText = findViewById(R.id.showingText);
	    mQRCodeImage = findViewById(R.id.myaddressqrcode);
		mReceiveFile = findViewById(R.id.receiveFile);
	    mAddFriendButton = findViewById(R.id.addfriend);
	    mAddFriendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCamera();
            }
        });

	    Button selectButton = findViewById(R.id.selectFile);
	    selectButton.setOnClickListener(new View.OnClickListener() {
		    @Override
		    public void onClick(View view) {
			    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			    intent.setType("*/*");
			    intent.addCategory(Intent.CATEGORY_OPENABLE);
			    startActivityForResult(intent, SELECTFILE_REQUEST_CODE);
		    }
	    });

	    mTransferButton = findViewById(R.id.filetransfer);
	    mTransferButton.setOnClickListener(new View.OnClickListener() {
		    @Override
		    public void onClick(View view) {
			    String path = mFilePath.getText().toString();
			    Log.d(TAG, "Sending file path="+path);
			    if (path.isEmpty()) {
				    ShowText("Path is empty.");
				    return;
			    }

			    if (sSimpleCarrier == null) {
				    ShowText("Carrier is null");
			    	return;
			    }

			    sSimpleCarrier.sendFile(path);
			    mReceiveFile.setImageBitmap(BitmapFactory.decodeFile(path));
		    }
	    });

	    requestWriteAndReadExternalPermission();

	    mHandler = new CarrierHandler();

	    sSimpleCarrier = SimpleCarrier.getInstance(MainActivity.this, mHandler);
	    mQRCodeImage.setImageBitmap(QRCodeUtils.createQRCodeBitmap(sSimpleCarrier.MyAddress()));
	    Log.d(TAG, String.format("MyAddress=[%s]",sSimpleCarrier.MyAddress()));
    }

    private void ShowText(String text) {
	    Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
    }

	public class CarrierHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case SimpleCarrier.ONREADY: {
					mAddFriendButton.setEnabled(true);
					mTransferButton.setEnabled(true);
					String text = (String)msg.obj;
					ShowText(text);

					break;
				}
				case SimpleCarrier.FRIENDONLINE : {
					String id = (String)msg.obj;
					mFriendId.setText(id + " : ");
					if (msg.arg1 == 1) {
						mFriendOnline.setTextColor(Color.GREEN);
						mFriendOnline.setText("Online");
					}
					else {
						mFriendOnline.setText("Offline");
						mFriendOnline.setTextColor(Color.BLACK);
					}
					break;
				}
				case SimpleCarrier.SHOWING: {
					String text = (String)msg.obj;
					ShowText(text);
					break;
				}
				case SimpleCarrier.SHOWINGTEXT: {
					String text = (String)msg.obj;
					mShowingText.setText(text);
					break;
				}
				case SimpleCarrier.SENDINGDATA: {
					String fileId = (String)msg.obj;
					sendData(fileId);
					break;
				}
				case SimpleCarrier.SHOWINGFILEPATH: {
					String filePath = (String)msg.obj;
					mReceiveFile.setImageBitmap(BitmapFactory.decodeFile(filePath));
					mShowingText.setText("Received file: " + filePath);
					break;
				}
				case SimpleCarrier.TRANSFERSTATE: {
					String state = (String)msg.obj;
					if (FileTransferState.Failed.toString().equals(state)) {
						//Font color: Red
						mFileTransferState.setTextColor(Color.RED);
					}
					else {
						//Font color: green
						mFileTransferState.setTextColor(Color.GREEN);
					}

					mFileTransferState.setText(state);
					break;
				}
			}
		}
	}

	private void sendData(final String fileId) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					sSimpleCarrier.sendData(fileId);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
		} }).start();
	}

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    super.onActivityResult(requestCode,resultCode,data);

	    IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
	    if (scanResult != null) {
		    String result = scanResult.getContents();
		    if (result != null && !result.isEmpty()) {
			    //TODO Add friend.
			    Log.d(TAG, String.format("onActivityResult==scanResult=[%s]",result));
			    sSimpleCarrier.AddFriend(result);
			    Toast.makeText(this,result, Toast.LENGTH_LONG).show();
		    }
		    return;
	    }

        if(requestCode == SELECTFILE_REQUEST_CODE) {
	    	if (data != null) {
			    String path = Utils.getPhotoPathFromContentUri(this, data.getData());
			    Log.d(TAG, "onActivityResult file path="+path);
			    mFilePath.setText(path);
		    }
        }
    }

    public void showCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        } else {
	        IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
	        integrator.initiateScan();
        }
    }

    private static final int REQUESTCAMERA = 0;
    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            Snackbar.make(mFileTransferState, "获取摄像头权限",
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUESTCAMERA);
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUESTCAMERA);
        }
    }

	@SuppressLint("NewApi")
	private void requestWriteAndReadExternalPermission() {
		if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			Log.d(TAG, "READ permission IS NOT granted...");

			if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
				Log.d(TAG, "shouldShowRequestPermissionRationale");
			} else {
				// 0 是自己定义的请求coude
				requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
			}
		} else {
			Log.d(TAG, "READ permission is granted...");
		}

		String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};

		for (String str : permissions) {
			if (this.checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this, permissions, 2);
			}
		}
	}
}
