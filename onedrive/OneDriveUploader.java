package onedrive;

import java.io.File;
import java.io.Serializable;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import onedrive.json.OneDriveItem;

/**
 * 第14章：ファイルを分割して OneDrive に転送するためのアップローダー。
 */
public class OneDriveUploader implements Serializable
{
  private static final long serialVersionUID = 6027223111576892994L;

  private static final int FRAGMENT_SIZE = 1024 *1024;

  public static final int STATUS_COMPLETED = 1;
  public static final int STATUS_CANCELED  = 2;
  public static final int STATUS_ERROR     = 3;

  private OneDriveItem item_folder;   // このフォルダにアップロードする。
  private OneDriveItem item_uploaded; // アップロードが完了した場合にこの変数に結果をセットする。
  private File file_local;
  private long size_sent; // 送信したバイト数。
  private long size_total; // 送信する予定のトータルバイト数。
  private String url_uploading; // ファイルをアップロードするためのURL
  private volatile boolean flag_uploading;
  private volatile boolean flag_request_cancel;
  private OneDrive client;
  private transient UploadListener listener;

  protected OneDriveUploader(OneDriveItem item_folder, File file_local, OneDrive client)
  {
    this.item_folder = item_folder;
    this.file_local  = file_local;
    this.size_total  = file_local.length();
    this.client      = client; // OneDrive クライアント
  }

  /**
   * アップロード処理を行う。
   */
  public int upload(OneDrive client)
  {
    if(size_total<=0 ||                 // ファイルサイズがゼロ。
       file_local.exists()==false ||    // ファイルが存在していない。
       file_local.length()!=size_total) // ファイルのサイズが以前と変わってしまった。
    {
      return STATUS_ERROR;
    }

    try
    {
      flag_uploading = true;
      flag_request_cancel = false;

      if(listener!=null)
      {
        listener.progress(this); // リスナーに進捗状況を報告。
      }

      Gson gson = new Gson();

      long position = 0;

      CloseableHttpClient http_client = null;
      CloseableHttpResponse response = null;

      try
      {
        http_client = HttpClientBuilder.create().build();

        if(url_uploading==null) // まだアップロードセッションが開始されていない。
        {
          // REST API の URLを生成。

          String url = OneDrive.URL_ONEDRIVE_API;
          String name = getLocalFile().getName();

          if(client.getApiMode()==OneDrive.MODE_API_ID) // アップロード先のフォルダをIDで指定するモード。
          {
            url += "/drive/items/" + item_folder.id + ":/";
            url += URLEncoder.encode(name, "UTF-8");
          }
          else if(client.getApiMode()==OneDrive.MODE_API_PATH) // アップロード先のフォルダをパスで指定するモード。
          {
            url += URLEncoder.encode(item_folder.getPath() + "/" + name, "UTF-8");
          }

          url += ":/upload.createSession";
          url += "?access_token=" + client.getAccessToken();

          // HTTP通信を行う。

          OneDriveCreateUploadSession create_session = new OneDriveCreateUploadSession();
          String json_send = gson.toJson(create_session);
          HttpPost request = new HttpPost(url);
          request.setEntity(new StringEntity(json_send));
          request.addHeader("Content-type", "application/json");
          response = http_client.execute(request);
          int code = response.getStatusLine().getStatusCode();

          if(code==HttpStatus.SC_OK)
          {
            HttpEntity entity = response.getEntity();
            String json = EntityUtils.toString(entity);
            OneDriveUploadSessionInformation info = gson.fromJson(json, OneDriveUploadSessionInformation.class);

            int index_access_token = info.uploadUrl.indexOf("?access_token=");

            if(index_access_token>0)
            {
              url_uploading = info.uploadUrl.substring(0, index_access_token); // URLに含まれているアクセストークンを分離する。
            }
          }
          else
          {
            return STATUS_ERROR;
          }

          response.close();
          response = null;
        }
        else // すでにアップロードセッションは開始されている。
        {
          // 過去にどこまでアップロードしたのかサーバーに問い合わせる。

          HttpGet request = new HttpGet(url_uploading + "?access_token=" + client.getAccessToken());
          response = http_client.execute(request);
          int code = response.getStatusLine().getStatusCode();

          if(code==HttpStatus.SC_OK)
          {
            HttpEntity entity = response.getEntity();
            String json_received = EntityUtils.toString(entity);
            OneDriveUploadSessionInformation info = gson.fromJson(json_received, OneDriveUploadSessionInformation.class);

            if(info!=null && info.nextExpectedRanges!=null && info.nextExpectedRanges.size()>0)
            {
              String range = info.nextExpectedRanges.get(0); // 次にアップロードすべきバイト範囲。
              position = Long.parseLong(range.split("-")[0]); // このバイト位置からアップロードを再開する。
            }
          }
          else
          {
            return STATUS_ERROR;
          }

          response.close();
          response = null;
        }

        // ファイルを分割してアップロード。

        long length_total = file_local.length(); // ファイルのトータルサイズ。

        byte[] buffer = new byte[FRAGMENT_SIZE];

        while(true)
        {
          if(flag_request_cancel==true) // アップロードのキャンセル要求がなされている。
          {
            return STATUS_CANCELED;
          }

          // アップロード用のURLにアクセストークンを付加する。

          HttpPut request = new HttpPut(url_uploading + "?access_token=" + client.getAccessToken());
          long range_start = position;
          long range_end   = Math.min(position + FRAGMENT_SIZE, length_total) - 1;
          String content_range = "bytes " + position + "-" + range_end + "/" + length_total;
          request.addHeader("Content-Range", content_range);
          int length = (int)(range_end-range_start+1); // 終了位置 - 開始位置 + 1 バイト送信される。
          length = client.getByteArrayFromFile(buffer, file_local, range_start, length);

          if(length<=0)
          {
            return STATUS_ERROR;
          }

          // 注：最新バージョンの Apache HttpClient の場合は、下記の処理を
          // ByteArrayEntity(buffer, 0, length) と記述することが可能です。
          // しかし、アンドロイド環境など Apache HttpClient のバージョンが古い場合は
          // 引数が3つのByteArrayEntityコンストラクタが実装されていません。
          // そのようなケースでもコンパイルできるように、下記では getByteArray()メソッドを使って
          // 配列のバイト数と実際に配列に読み込んだバイト数が異なるケースに対応しています。

          ByteArrayEntity entry = new ByteArrayEntity(getByteArray(buffer, length));

          request.setEntity(entry);
          response = http_client.execute(request);
          int code = response.getStatusLine().getStatusCode();

          if(code==HttpStatus.SC_ACCEPTED) // ファイルの一部がサーバーに受理された。
          {
            HttpEntity entity = response.getEntity();
            String json = EntityUtils.toString(entity);
            OneDriveUploadSessionInformation info = gson.fromJson(json, OneDriveUploadSessionInformation.class);

            if(info!=null && info.nextExpectedRanges!=null && info.nextExpectedRanges.size()>0)
            {
              String range = info.nextExpectedRanges.get(0); // 次にアップロードすべきバイト範囲。
              position = Long.parseLong(range.split("-")[0]); // このバイト位置から続きをアップロードする。
              setSentBytes(position); // 送信済みのバイト数を記録。
            }
          }
          else if(code==HttpStatus.SC_CREATED || // ファイルが新規作成された。
                  code==HttpStatus.SC_OK)        // 既存のファイルが上書きされた。
          {
            HttpEntity entity = response.getEntity();
            String json = EntityUtils.toString(entity);
            OneDriveItem item = gson.fromJson(json, OneDriveItem.class);
            setUploadedItem(item);
            setSentBytes(item.size); // 送信済みのバイト数を記録。

            // 第16章：最終更新日時をセット。
            Date modified = new Date(file_local.lastModified());
            item = client.setFileSystemInfo(item, modified, null); // ローカルの最終更新日時を OneDrive上にセットする。

//            // 第16章：作成日時と最終更新日時をセット。(下記の処理は、Androidでは使用できません。)
//            Path path = Paths.get(file_local.getAbsolutePath());
//            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
//            Date created = new Date(attributes.creationTime().toMillis());
//            Date modified = new Date(file_local.lastModified());
//            item = client.setFileSystemInfo(item, created, modified); // ローカルの作成日時と最終更新日時を OneDrive上にセットする。

            if(item!=null)
            {
              setUploadedItem(item);
            }

            return STATUS_COMPLETED;
          }
          else
          {
            return STATUS_ERROR;
          }

          response.close();
          response = null;

          if(this.listener!=null)
          {
            this.listener.progress(this);; // リスナーに進捗状況を報告。
          }
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
      finally
      {
        try{if(response   !=null) response.close();}   catch (Exception e){e.printStackTrace();}
        try{if(http_client!=null) http_client.close();}catch (Exception e){e.printStackTrace();}
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      this.flag_uploading = false;

      if(listener!=null)
      {
        listener.progress(this); // 最後にリスナーに進捗状況を報告。
      }
    }

    return STATUS_ERROR;
  }

  /**
   * 受け取ったバイトアレイの指定した範囲をコピーしたバイト配列を返すメソッドです。
   * アンドロイド用のコードと一般的な Java用のコードの互換性を保つために
   * 上記の upload()メソッド内で使用しています。
   */
  private byte[] getByteArray(byte[] buffer_source, int length)
  {
    if(buffer_source.length==length) // 配列の長さと length が同じ場合。
    {
      return buffer_source; // 受け取ったバイト列をそのまま返す。
    }
    else // 配列の長さと length が異なる場合。
    {
      byte[] buffer_dest = new byte[length];
      System.arraycopy(buffer_source, 0, buffer_dest, 0, length);
      return buffer_dest; // サイズを調節したバイト列を返す。
    }
  }

  /**
   * アップロードが完了したアイテムをセットする。
   */
  private synchronized void setUploadedItem(OneDriveItem item)
  {
    this.item_uploaded = item;
  }

  /**
   * アップロードが完了したアイテムを返す。
   */
  public synchronized OneDriveItem getUploadedItem()
  {
    return this.item_uploaded;
  }

  /**
   * アップロードする先のフォルダーのアイテムを返す。
   */
  public OneDriveItem getFolderItem()
  {
    return this.item_folder;
  }

  /**
   * アップロードが完了したかどうかを返す。
   */
  public synchronized boolean isCompleted()
  {
    return (this.item_uploaded!=null);
  }

  /**
   * アップロード中かどうかを返す。
   */
  public synchronized boolean isUploading()
  {
    return this.flag_uploading;
  }

  /**
   * アップロード済みのバイト数をセットする。
   */
  public synchronized void setSentBytes(long size)
  {
    this.size_sent = size;
  }

  /**
   * アップロード済みのバイト数を返す。
   */
  public synchronized long getSentBytes()
  {
    return this.size_sent;
  }

  /**
   * アップロードするファイルのサイズを返す。
   */
  public synchronized long getTotalBytes()
  {
    return this.size_total;
  }

  /**
   * アップロードの進捗状況を返す。
   */
  public synchronized double getProgress()
  {
    return (double)this.getSentBytes() / (double)this.getTotalBytes() * 100.0;
  }

  /**
   * アップロードするファイルを返す。
   */
  public File getLocalFile()
  {
    return this.file_local;
  }

  /**
   * アップロードの中断を要求する。
   */
  public void requestCancel()
  {
    this.flag_request_cancel = true; // 中断要求がなされた。
  }

  /**
   * ファイルの分割アップロードのセッションを開始する際にローカルからサーバーへ送信するために使うJSONオブジェクトの「器」。
   */
  private class OneDriveCreateUploadSession
  {
    OneDriveCreateUploadSessionItem item = new OneDriveCreateUploadSessionItem("rename");
  }

  /**
   * ファイルの分割アップロードのセッションを開始する際にローカルからサーバーへ送信するために使うJSONオブジェクトの「器」。
   */
  private class OneDriveCreateUploadSessionItem
  {
    @SerializedName("@name.conflictBehavior")
    String name_conflictBehavior = "rename";  // "rename" か "replace" か "fail" を指定してください。

    public OneDriveCreateUploadSessionItem(String name_conflictBehavior)
    {
      this.name_conflictBehavior = name_conflictBehavior;
    }
  }

  /**
   * ファイルの分割アップロードのセッションの情報をサーバーから受信するためのJSONオブジェクトの「器」。
   */
  private class OneDriveUploadSessionInformation
  {
    String uploadUrl;
    String expirationDateTime;
    List<String> nextExpectedRanges;
    @SerializedName("@odata.context")
    String odata_context;
  }

  /**
   * アップロード中の進捗状況を返すリスナーをセット。
   */
  public void setUploadListener(UploadListener listener)
  {
    this.listener = listener;
  }

  /**
   * アップロード中の進捗状況を返すリスナー。
   */
  public interface UploadListener
  {
    void progress(OneDriveUploader uploader);
  }

  /**
   * OneDriveのアップローダーリストから自分自身を取り除く。
   */
  public void remove()
  {
    client.removeUploader(this);
  }
}