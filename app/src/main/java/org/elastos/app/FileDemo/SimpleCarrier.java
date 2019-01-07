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
	static final int ONREADY = 0;
	static final int TRANSFERSTATE = 1;
	static final int FRIENDONLINE = 2;
	static final int SHOWING = 3;
	static final int SHOWINGTEXT = 4;
	static final int SHOWINGFILEPATH = 5;
	static final int SENDINGDATA = 6;

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
			FileTransferInfo currentFileTransferInfo = new FileTransferInfo(filePath, fileId, file.length());

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
	private static long sReceiveFileSize = 0;
	static class TransferHandler implements FileTransferHandler {
		@Override
		public void onStateChanged(FileTransfer filetransfer, FileTransferState state) {
			sendMessage(TRANSFERSTATE, state.toString());
			Log.d(TAG, String.format("onStateChanged==state=[%s]",state.toString()));
		}

		@Override
		public void onFileRequest(FileTransfer filetransfer, String fileId, String filename, long size) {
			sReceiveDataLen = 0;
			sReceiveFileSize = size;

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
			sendShowingMessage("Start Sending data");
			sendMessage(SENDINGDATA, fileId);
		}

		@Override
		public boolean onData(FileTransfer filetransfer, String fileId, byte[] data) {
			sReceiveDataLen += data.length;
			String percentString = "" + (sReceiveDataLen * 100 / sReceiveFileSize) + "%";
			sendMessage(SHOWINGTEXT, String.format(Locale.US, "Receiving File: Length=[%d], Percent=[%s]", sReceiveDataLen, percentString));

			String filePath = sBasePath + "/" + fileId.substring(0, 8);
			Utils.byte2File(data, filePath);

			if (sReceiveFileSize == sReceiveDataLen) {
				//TODO: show the File path.
				sendMessage(SHOWINGFILEPATH, filePath);
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

	void sendData(String fileId) {
		try {
			if (sSendFilePath != null && !sSendFilePath.isEmpty()) {
				File file = new File(sSendFilePath);
				if (file.isFile()) {
					final long FILESIZE = file.length();
					sendMessage(SHOWINGTEXT, String.format(Locale.US, "sendData start: fileSize=[%d]", FILESIZE));
					FileInputStream fis;
					try {
						final int SIZE = 1024;
						fis = new FileInputStream(file);
						byte[] buffer = new byte[SIZE];
						int len;
						long sent = 0;
						String percentString;
						while ((len = fis.read(buffer)) != -1) {
							//write Data
							writeData(fileId, buffer, len);
							sent += len;
							Log.d(TAG, String.format(Locale.US, "sending len=[%d]", sent));
							percentString = "" + (sent * 100 / FILESIZE) + "%";
							sendMessage(SHOWINGTEXT, String.format(Locale.US, "Sending: Sent=[%d], Percent=[%s]", sent, percentString));
						}
					}
					catch (IOException e) {
						e.printStackTrace();
					}
					Log.d(TAG, "sendData==================Finish");
					sendShowingMessage("Sending file Finished.");
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			sSendFilePath = "";
		}
	}

	private static void writeData(String fileId, byte[] data, int len)
	{
		int pos = 0;
		int rc;

		int left = len;
		while(left > 0) {
			try {
				rc = sFileTransfer.writeData(fileId, data, pos, left);
				pos += rc;
				left -= rc;
			}
			catch (CarrierException e) {
				int errorCode = e.getErrorCode();
				Log.d(TAG, String.format("Write data failed (0x%x)", errorCode));
				e.printStackTrace();
			}
		}
	}

	static class CarrierHandler extends AbstractCarrierHandler {
		@Override
		public void onReady(Carrier carrier) {
			Log.d(TAG, "===onReady===");
			sendMessage(ONREADY, "onReady");
		}

		@Override
		public void onFriendRequest(Carrier carrier, String userId, UserInfo info, String hello) {
			try {
				sendShowingMessage("onFriendRequest:" + userId);
				carrier.acceptFriend(userId);
			}
			catch (CarrierException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onFriendConnection(Carrier carrier, String friendId, ConnectionStatus status) {
			sendShowingMessage("onFriendConnection:" + friendId);
			Log.d(TAG, "onFriendConnection= status="+status);

			Message msg = new Message();
			msg.what = FRIENDONLINE;
			msg.arg1 = 0;
			sCurrentUserId = null;
			if (status == ConnectionStatus.Connected) {
				sCurrentUserId = friendId;
				msg.arg1 = 1;
			}

			msg.obj = "Friend " + friendId.substring(0, 8);
			mHandler.sendMessage(msg);
		}
	}

	private static void sendShowingMessage(String content) {
		Message msg = new Message();
		msg.what = SHOWING;
		msg.obj = content;
		mHandler.sendMessage(msg);
	}

	private static void sendMessage(int what, String content) {
		Message msg = new Message();
		msg.what = what;
		msg.obj = content;
		mHandler.sendMessage(msg);
	}
}
