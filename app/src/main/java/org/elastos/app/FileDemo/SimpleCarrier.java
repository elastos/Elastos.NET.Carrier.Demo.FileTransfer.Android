package org.elastos.app.FileDemo;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.elastos.carrier.AbstractCarrierHandler;
import org.elastos.carrier.Carrier;
import org.elastos.carrier.ConnectionStatus;
import org.elastos.carrier.UserInfo;
import org.elastos.carrier.exceptions.CarrierException;
import org.elastos.carrier.filetransfer.FileTransfer;
import org.elastos.carrier.filetransfer.FileTransferHandler;
import org.elastos.carrier.filetransfer.FileTransferInfo;
import org.elastos.carrier.filetransfer.FileTransferState;
import org.elastos.carrier.filetransfer.Manager;
import org.elastos.carrier.filetransfer.ManagerHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

class SimpleCarrier {
	private static final String TAG = "SimpleCarrier";
	private static SimpleCarrier sSimpleCarrier;
	private static Carrier sCarrier;
	private static Manager sFileTransferManager;
	private static FileTransfer sFileTransfer;
	private TransferHandler mTransferHandler;
	private static Handler mHandler;
	private static String sBasePath;

	//MSG TYPE
	static final int ONREADY = 1;
	static final int FRIENDONLINE = 2;
	static final int SHOWING = 3;
	static final int SHOWINGTEXT = 4;
	static final int SHOWINGFILE = 5;

	private static String sCurrentUserId = null;

	static SimpleCarrier getInstance(Context context, Handler handler) {
		if (sSimpleCarrier == null) {
			sSimpleCarrier = new SimpleCarrier(context, handler);
		}

		return sSimpleCarrier;
	}

	static SimpleCarrier getInstance() {
		return sSimpleCarrier;
	}

	void AddFriend(String address) {
		try {
			sCarrier.addFriend(address, "hello");
		}
		catch (CarrierException e) {
			e.printStackTrace();
		}
	}

	private static String sSendFilePath = null;
	void sendFile(String filePath) {
		try {
			if (sCurrentUserId == null) {
				sendShowingMessage("The friend is Offline");
			}

			sSendFilePath = filePath;
			String fileId = FileTransfer.generateFileId();
			File file = new File(filePath);
			Log.i(TAG, "sendFile file name="+file.getName());
			FileTransferInfo currentFileTransferInfo = new FileTransferInfo(file.getName(), fileId, file.length());

			if (sFileTransfer == null) {
				sFileTransfer = sFileTransferManager.newFileTransfer(sCurrentUserId, currentFileTransferInfo, mTransferHandler);
				sFileTransfer.connect();
			}
			else {
				sFileTransfer.addFile(currentFileTransferInfo);
			}
		}
		catch (CarrierException e) {
			e.printStackTrace();
		}
	}

	String MyAddress() {
		try {
			return sCarrier.getAddress();
		}
		catch (CarrierException e) {
			e.printStackTrace();
		}
		return "";
	}

	private SimpleCarrier(Context context, Handler handler) {
		mTransferHandler = new TransferHandler();
		mHandler = handler;
		sBasePath = context.getFilesDir().getParent();
		CarrierOptions options = new CarrierOptions(sBasePath);
		CarrierHandler carrierHandler = new CarrierHandler();

		try {
			Carrier.initializeInstance(options, carrierHandler);
			sCarrier = Carrier.getInstance();
			sCarrier.start(0);
			Log.i(TAG, "Carrier node is ready now");

			//Initialize fileTransfer instance.
			FileTransferManagerHandler fileTransferManagerHandler = new FileTransferManagerHandler();
			Manager.initializeInstance(sCarrier, fileTransferManagerHandler);
			sFileTransferManager = Manager.getInstance();
		}
		catch (CarrierException /*| InterruptedException*/ e) {
			e.printStackTrace();
			Log.e(TAG, "Carrier node start failed, abort this test.");
		}
	}

	class FileTransferManagerHandler implements ManagerHandler {
		@Override
		public void onConnectRequest(Carrier carrier, String from, FileTransferInfo info) {
			sendShowingMessage("onConnectRequest, info="+info);
			try {
				sFileTransfer = sFileTransferManager.newFileTransfer(from, info, mTransferHandler);
				sFileTransfer.acceptConnect();
			}
			catch (CarrierException e) {
				e.printStackTrace();
			}
		}
	}

	private static long sReceiveDataLen = 0;
	private static int sReceiveDataCount = 0;
	private static long sReceiveFileSize = 0;
	private static byte[] sReceiveFileData;
	static class TransferHandler implements FileTransferHandler {
		@Override
		public void onStateChanged(FileTransfer filetransfer, FileTransferState state) {
			Log.d(TAG, String.format("onStateChanged==state=[%s]",state.toString()));
		}

		@Override
		public void onFileRequest(FileTransfer filetransfer, String fileId, String filename, long size) {
			sReceiveDataLen = 0;
			sReceiveDataCount = 0;
			sReceiveFileSize = size;
			sReceiveFileData = new byte[(int)size];

			sendShowingMessage("onFileRequest");

			//Pull file
			try {
				sFileTransfer.pullData(fileId, 0);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onPullRequest(FileTransfer filetransfer, String fileId, long offset) {
			sendShowingMessage("onPullRequest");

			try {
				if (sSendFilePath != null && !sSendFilePath.isEmpty()) {
					byte[] data = getFileData(sSendFilePath);

					//write Data
					writeData(fileId, data);
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				sSendFilePath = "";
			}
		}

		@Override
		public boolean onData(FileTransfer filetransfer, String fileId, byte[] data) {
			System.arraycopy(data, 0, sReceiveFileData, (int)sReceiveDataLen, data.length);

			sReceiveDataCount ++;
			sReceiveDataLen += data.length;
			sendShowingMessage(SHOWINGTEXT, String.format(Locale.US, "ReceiveData  count = [%d], Len=[%d]", sReceiveDataCount, sReceiveDataLen));

			if (sReceiveFileSize == sReceiveDataLen) {
				//TODO: show the image.
				String filePath = sBasePath + "/" + System.currentTimeMillis();
				Utils.byte2image(sReceiveFileData, filePath);
				sendShowingMessage(SHOWINGFILE, filePath);
			}
			return true;
		}

		@Override
		public void onDataFinished(FileTransfer filetransfer, String fileId) {
			Log.d(TAG, String.format("onDataFinished==fileId=[%s]",fileId));
		}

		@Override
		public void onPending(FileTransfer filetransfer, String fileId) {
			Log.d(TAG, String.format("onPending==fileid=[%s]",fileId));
		}

		@Override
		public void onResume(FileTransfer filetransfer, String fileId) {
			Log.d(TAG, String.format("onResume==fileid=[%s]",fileId));
		}

		@Override
		public void onCancel(FileTransfer filetransfer, String fileId, int status, String reason) {
			Log.d(TAG, String.format("onCancel==fileid=[%s], status=[%d], reason=[%s]",fileId, status, reason));
		}
	}

	private static void writeData(String fileId, byte[] data)
	{
		final int SIZE = 512/*1024*/;
		final int LEN = data.length;
		int flag = LEN % SIZE;

		int COUNT = LEN / SIZE;
		if (flag > 0) {
			COUNT += 1;
		}

		sendShowingMessage(SHOWINGTEXT, String.format(Locale.US, "writeData  COUNT=[%d], SIZE=[%d], len=[%d]", COUNT, SIZE, data.length));
		int pos = 0;
		int rc;

		int left;
		for (int i = 0; i < COUNT; i++) {
			left = SIZE;

			//Last
			if (i == (COUNT -1)) {
				left = LEN - i * SIZE;
			}

			while(left > 0) {
				try {
					rc = sFileTransfer.writeData(fileId, data, pos, left);
					pos += rc;
					left -= rc;
					Log.d(TAG, String.format("writeData len = [%d]", rc));
				}
				catch (CarrierException e) {
					int errorCode = e.getErrorCode();
					Log.d(TAG, String.format("Write data failed (0x%x)", errorCode));
					e.printStackTrace();
				}
			}
		}

		Log.d(TAG, String.format("writeData=[%s]=================","Finish"));
	}

	static class CarrierHandler extends AbstractCarrierHandler {
		@Override
		public void onReady(Carrier carrier) {
			sendMessage();
		}

		@Override
		public void onFriendRequest(Carrier carrier, String userId, UserInfo info, String hello) {
			try {
				carrier.acceptFriend(userId);
			}
			catch (CarrierException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onFriendConnection(Carrier carrier, String friendId, ConnectionStatus status) {
			Log.d(TAG, "onFriendConnection= status="+status);

			Message msg = new Message();
			msg.what = FRIENDONLINE;
			msg.obj = "The friend is Offline";
			sCurrentUserId = null;
			if (status == ConnectionStatus.Connected) {
				msg.obj = "The friend is Online";
				sCurrentUserId = friendId;
			}
			mHandler.sendMessage(msg);
		}
	}

	private static byte[] getFileData(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return null;
		}

		File file = new File(filePath);
		if (file.isFile()) {
			FileInputStream fis;
			try {
				fis = new FileInputStream(file);
				byte[] buffer = new byte[1024];
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				int len;
				while ((len = fis.read(buffer)) != -1) {
					outputStream.write(buffer, 0, len);
				}

				return outputStream.toByteArray();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		return null;
	}


	private static void sendMessage() {
		Message msg = new Message();
		msg.what = ONREADY;
		mHandler.sendMessage(msg);
	}

	private static void sendShowingMessage(String content) {
		Message msg = new Message();
		msg.what = SHOWING;
		msg.obj = content;
		mHandler.sendMessage(msg);
	}

	private static void sendShowingMessage(int what, String content) {
		Message msg = new Message();
		msg.what = what;
		msg.obj = content;
		mHandler.sendMessage(msg);
	}
}
