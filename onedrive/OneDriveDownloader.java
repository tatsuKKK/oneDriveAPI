package onedrive;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import onedrive.json.OneDriveItem;

/**
 * 第11章：OneDriveからのファイルダウンローダー。
 */
public class OneDriveDownloader implements Serializable
{
  private static final long serialVersionUID = 8074205805758477363L;

  private static final int FRAGMENT_SIZE = 1024 * 1024;

  public static final int STATUS_DOWNLOADING = 0;
  public static final int STATUS_COMPLETED   = 1;
  public static final int STATUS_CANCELED    = 2;
  public static final int STATUS_ERROR       = 3;

  private OneDriveItem item; // このダウンロードするファイル。
  private File file_dest; // 保存先ファイル。
  private File file_temp; // テンポラリファイル。
  private long size_received; // すでに受信したバイト数。
  private volatile boolean flag_downloading = false; // ダウンロード中かどうか。
  private volatile boolean flag_cancel_requested = false; // キャンセル要求がされているかどうか。
  private volatile boolean flag_completed = false; // ダウンロードが完了したかどうか。
  private OneDrive client;
  private URL url_download;
  private transient DownloadListener listener;

  protected OneDriveDownloader(OneDriveItem item, File file_dest, OneDrive client)
  {
    this.item      = item;      // OneDrive上のアイテム。
    this.file_dest = file_dest; // ダウンロード先のファイル。
    this.client    = client;    // OneDrive クライアント

    try
    {
      this.file_temp = File.createTempFile("OneDriveDownloader", ".tmp");
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  /**
   * ダウンロード処理を行う。
   */
  public int download()
  {
    try
    {
      flag_downloading = true;
      flag_cancel_requested = false;

      if(listener!=null)
      {
        listener.progress(this); // リスナーに進捗状況を報告。
      }

      CloseableHttpClient http_client = null;
      CloseableHttpResponse response = null;

      try
      {
        // REST API の URLを生成。

        String url = OneDrive.URL_ONEDRIVE_API;

        if(client.getApiMode()==OneDrive.MODE_API_ID && item!=null) // IDで指定するモード。
        {
          url += "/drive/items/" + item.id;
        }
        else if(client.getApiMode()==OneDrive.MODE_API_PATH) // パスで指定するモード。
        {
          url += URLEncoder.encode(item.getPath(), "UTF-8") + ":";
        }

        url += "/content";
        url += "?access_token=" + client.getAccessToken();

        // HTTP通信を行う。

        http_client = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet(url);
        response = http_client.execute(request);
        int code = response.getStatusLine().getStatusCode();

        if(code==HttpStatus.SC_OK)
        {
          Header header = response.getFirstHeader("Content-Location");
          url_download = new URL(header.getValue()); // ダウンロード用のURLを表す文字列がヘッダに格納されている。
        }
        else
        {
          return STATUS_ERROR;
        }
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
      finally
      {
        try{if(response   !=null) response.close();}   catch (Exception e){e.printStackTrace();}
        try{if(http_client!=null) http_client.close();}catch (Exception e){e.printStackTrace();}
      }

      if(url_download==null)
      {
        return STATUS_ERROR;
      }

      int status = STATUS_DOWNLOADING;

      InputStream stream_in   = null;
      OutputStream stream_out = null;

      try
      {
        URLConnection connection = url_download.openConnection();
        connection.setConnectTimeout(5000); // 接続タイムアウトを5秒に設定。
        connection.setReadTimeout(5000); // 読み込みのタイムアウトを5秒に設定。

        long length = file_temp.length();
        boolean flag_append = (length>0); // 続きを読み込むかどうかのフラグ。

        if(flag_append==true)
        {
          setReceivedBytes(length);
          connection.setRequestProperty("Range", "bytes=" + size_received + "-" + item.size); // ダウンロードの開始位置を指定。
        }

        byte[] buffer = new byte[FRAGMENT_SIZE];

        stream_in = new BufferedInputStream(connection.getInputStream());
        stream_out = new BufferedOutputStream(new FileOutputStream(file_temp, flag_append));

        while(true)
        {
          if(flag_cancel_requested==true) // キャンセル要求が来ている。
          {
            return STATUS_CANCELED;
          }

          try
          {
            int size =  stream_in.read(buffer);

            if(size>0)
            {
              stream_out.write(buffer, 0, size); // 受信したバイト列をテンポラリファイルに書き出す。
              stream_out.flush();

              setReceivedBytes(getReceivedBytes() + size); // 受信したバイト数を記録。

              if(listener!=null)
              {
                listener.progress(this); // リスナーに進捗状況を報告。
              }
            }
            else // ダウンロードが完了した場合。
            {
              status = STATUS_COMPLETED;
              break;
            }
          }
          catch(SocketTimeoutException e) // タイムアウトした場合。
          {
            Thread.sleep(10*1000); // 10秒待ってからリトライ。
          }
          catch(Exception e) // その他の例外。
          {
            break;
          }
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
      finally
      {
        try{if(stream_in !=null) stream_in.close() ;}catch (Exception e){e.printStackTrace();}
        try{if(stream_out!=null) stream_out.close();}catch (Exception e){e.printStackTrace();}
      }

      if(status==STATUS_COMPLETED)
      {
        // テンポラリファイルをユーザーが指定したパスのファイルにリネームする。

        if(file_dest.exists()==true) // すでに同名のファイルが存在している場合。
        {
          file_dest.delete(); // 削除する。
        }

        if(file_temp.renameTo(file_dest)==true)
        {
          flag_completed = true;
          return STATUS_COMPLETED;
        }
        else
        {
          return STATUS_ERROR;
        }
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      flag_downloading = false;

      if(listener!=null)
      {
        listener.progress(this); // 最後にリスナーに進捗状況を報告。
      }
    }

    return STATUS_ERROR;
  }

  /**
   * 受信したバイト数をセット。
   */
  public synchronized void setReceivedBytes(long size)
  {
    size_received = size;
  }

  /**
   * 受信したバイト数を返す。
   */
  public synchronized long getReceivedBytes()
  {
    return size_received;
  }

  /**
   * アイテム全体のバイト数を返す。
   */
  public synchronized long getTotalBytes()
  {
    return item.size;
  }

  /**
   * ダウンロードの進捗状況を返す。
   */
  public synchronized double getProgress()
  {
    if(isCompleted()==true)
    {
      return 100.0;
    }
    else
    {
      if(getTotalBytes()>0.0)
      {
        return (double)getReceivedBytes() / (double)getTotalBytes() * 100.0;
      }
    }

    return 0.0;
  }

  /**
   * 現在ダウンロード中かどうかを返す。
   */
  public synchronized boolean isDownloading()
  {
    return flag_downloading;
  }

  /**
   * ダウンロードするアイテムを返す。
   */
  public OneDriveItem getItem()
  {
    return item;
  }

  /**
   * ダウンロード先のファイルを返す。
   */
  public File getLocalFile()
  {
    return file_dest;
  }

  /**
   * ダウンロードのキャンセルを要求。
   */
  public void requestCancel()
  {
    flag_cancel_requested = true; // 中断要求がなされた。
  }

  /**
   * ダウンロードが完了したかどうかを返す。
   */
  public boolean isCompleted()
  {
    return flag_completed;
  }

  /**
   * ダウンロード中の進捗状況を返すリスナーをセット。
   */
  public void setDownloadListener(DownloadListener listener)
  {
    this.listener = listener;
  }

  /**
   * ダウンロード中の進捗状況を返すリスナー。
   */
  public interface DownloadListener
  {
    void progress(OneDriveDownloader downloader);
  }

  /**
   * OneDrive のダウンローダーリストから自分自身を取り除く。
   */
  public void remove()
  {
    client.removeDownloader(this);
  }
}