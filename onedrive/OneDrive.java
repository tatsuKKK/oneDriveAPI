package onedrive;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import onedrive.json.OneDriveFileSystemInfo;
import onedrive.json.OneDriveIdentity;
import onedrive.json.OneDriveItem;
import onedrive.json.OneDriveItemList;
import onedrive.json.OneDriveItemReference;
import onedrive.json.OneDriveProgressMonitor;
import onedrive.json.OneDriveToken;

public class OneDrive implements Serializable
{
  private static final long serialVersionUID = -8438921066276963567L;

  public static final String SRF_SIGN_IN  = "oauth20_authorize.srf";
  public static final String SRF_SIGN_OUT = "oauth20_logout.srf";
  public static final String SRF_REDIRECT = "oauth20_desktop.srf";
  public static final String SRF_TOKEN    = "oauth20_token.srf";

  public static final String URL_AUTHORIZE = "https://login.live.com/";
  public static final String URL_SIGN_IN   = URL_AUTHORIZE + SRF_SIGN_IN;
  public static final String URL_SIGN_OUT  = URL_AUTHORIZE + SRF_SIGN_OUT;
  public static final String URL_REDIRECT  = URL_AUTHORIZE + SRF_REDIRECT;
  public static final String URL_TOKEN     = URL_AUTHORIZE + SRF_TOKEN;

  public static final String URL_ONEDRIVE_API = "https://api.onedrive.com/v1.0";

  public static final String SCOPE_SIGNIN             = "wl.signin";
  public static final String SCOPE_OFFLINE_ACCESS     = "wl.offline_access";
  public static final String SCOPE_ONEDRIVE_READONLY  = "onedrive.readonly";
  public static final String SCOPE_ONEDRIVE_READWRITE = "onedrive.readwrite";
  public static final String SCOPE_ONEDRIVE_APPFOLDER = "onedrive.appfolder";

  public static final String LANGUAGE_ARABIC     = "ar"; // アラビア語
  public static final String LANGUAGE_CZECH      = "cs"; // チェコ語
  public static final String LANGUAGE_CHINESE_CN = "cn"; // 中国語（簡体字）
  public static final String LANGUAGE_CHINESE_TW = "tw"; // 中国語（繁体字）
  public static final String LANGUAGE_DENMARK    = "de"; // デンマーク語
  public static final String LANGUAGE_DUTCH      = "nl"; // オランダ語
  public static final String LANGUAGE_ENGLISH    = "en"; // 英語
  public static final String LANGUAGE_FRENCH     = "fr"; // フランス語
  public static final String LANGUAGE_GERMAN     = "de"; // デンマーク語
  public static final String LANGUAGE_GREECE     = "el"; // ギリシャ語
  public static final String LANGUAGE_ITALIAN    = "it"; // イタリア語
  public static final String LANGUAGE_JAPANESE   = "ja"; // 日本語
  public static final String LANGUAGE_KOREAN     = "ko"; // 韓国語
  public static final String LANGUAGE_POLISH     = "pl"; // ポーランド語
  public static final String LANGUAGE_PORTUGUESE = "pt"; // ポルトガル語
  public static final String LANGUAGE_RUSSIAN    = "ru"; // ロシア語
  public static final String LANGUAGE_SPANISH    = "es"; // スペイン語
  public static final String LANGUAGE_SWEDISH    = "sv"; // スウェーデン語
  public static final String LANGUAGE_THAI       = "th"; // 対語
  public static final String LANGUAGE_TURKIS     = "tr"; // トルコ語
  public static final String LANGUAGE_URUDU      = "ur"; // ウルドゥ語

  public static final int MODE_API_ID   = 0; // IDを指定するモード。
  public static final int MODE_API_PATH = 1; // パスを指定するモード。

  private String client_id;       // 第4章：クライアントID
  private String client_secret;   // 第6章：クライアントシークレット。
  private OneDriveToken tokens;   // 第6章：アクセストークンとリフレッシュトークン。
  private int mode = MODE_API_ID; // 第8章：APIモード。
  private transient HashMap<String, OneDriveItem> map_items; // 第8章：OneDriveItem のキャッシュ。
  private ArrayList<OneDriveDownloader> list_downloader; // 第11章：ダウンローダーのリスト
  private ArrayList<OneDriveUploader>   list_uploader;   // 第14章：アップローダーのリスト
  private transient HashMap<String, byte[]> map_thumbnails; // 第18章：サムネイル画像のキャッシュ。
  private String delta_token; // 第21章：最近の変更を取得するためのトークン。

  /**
   * 第4章：マイクロソフトの OAuth認証用サインイン用のWEBページのURLを生成する。
   */
  public String getSignInUrl(String language)
  {
    String url = null;

    try
    {
      if(language==null) // 第6章：表示言語の指定がない場合。
      {
        url = URL_SIGN_IN;
      }
      else // 第6章：表示言語が指定されている場合。
      {
        url = URL_AUTHORIZE + (language + "/" + SRF_SIGN_IN);
      }

      // 第7章：複数のスコープを指定するときは、スペース区切りで指定します。
      // ただし、URLの一部として送信するため、スペース文字を %20 に変換する必用があります。

      String scopes = URLEncoder.encode(SCOPE_OFFLINE_ACCESS + " "
          + SCOPE_ONEDRIVE_READWRITE + " " // 第12章：読み込み、書き込み権限。
          + SCOPE_ONEDRIVE_APPFOLDER,      // 第12章：アプリの専用フォルダにアクセスする権限。
          "UTF-8");

      url = url
          + "?client_id=" + getClientID()
          + "&scope=" + scopes
          + "&response_type=code"
          + "&redirect_uri=" + URL_REDIRECT;
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }

    return url;
  }

  /**
   * 第4章：マイクロソフトの OAuth認証サインアウト用のWEBページのURLを生成する。
   */
  public String getSignOutUrl()
  {
    String url = URL_SIGN_OUT
        + "?client_id=" + getClientID()
        + "&redirect_uri=" + URL_REDIRECT;

    return url;
  }

  /**
   * 第4章：クライアントIDをセットする。
   */
  public void setClientID(String client_id)
  {
    this.client_id = client_id;
  }

  /**
   * 第4章：クライアントIDを参照する。
   */
  public String getClientID()
  {
    return client_id;
  }

  /**
   * 第5章：マイクロソフトの OAuth 認証のWEBページからリダイレクトされたURLから認証コードを抜き出す。
   */
  public String getCodeFromUrl(String url)
  {
    String code = null;

    Pattern pattern = Pattern.compile("(" + URL_REDIRECT + "\\?code=" + ")(.*)(&lc=)(.*)"); // 正規表現。
    Matcher matcher = pattern.matcher(url);

    if(matcher.find()==true)
    {
      code = matcher.group(2);
    }

    return code;
  }

  /**
   * 第6章：サーバーに認証コードを渡してアクセストークンを取得する。
   */
  public boolean requestAccessTokenByCode(String auth_code)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_TOKEN // (1)
          + "?client_id=" + getClientID()
          + "&client_secret=" + getClientSecret()
          + "&code=" + auth_code
          + "&grant_type=authorization_code"
          + "&redirect_uri=" + URL_REDIRECT;

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build(); // (2)
      HttpGet request = new HttpGet(url); // (3)
      response = http_client.execute(request); // (4)
      int code = response.getStatusLine().getStatusCode(); // (5)

      if(code==HttpStatus.SC_OK)
      {
        HttpEntity entity = response.getEntity(); // (6)
        String json = EntityUtils.toString(entity); // (7)
        Gson gson = new Gson();
        tokens = gson.fromJson(json, OneDriveToken.class); // (8)
        tokens.setTime(System.currentTimeMillis()); // このアクセストークンを受け取った時刻を記録。
        return true;
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      try{if(response   !=null) response.close();}   catch(Exception e){e.printStackTrace();} // (9)
      try{if(http_client!=null) http_client.close();}catch(Exception e){e.printStackTrace();} // (10)
    }

    return false;
  }

  /**
   * 第6章：サーバーにリフレッシュトークンを渡してアクセストークンを取得する。
   */
  public boolean requestAccessTokenByRefreshToken()
  {
    if(isSignedIn()==false) // サインインしていない場合。
    {
      return false;
    }

    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_TOKEN // (1)
          + "?client_id=" + getClientID()
          + "&client_secret=" + getClientSecret()
          + "&refresh_token=" + getRefreshToken()
          + "&grant_type=refresh_token";

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build(); // (2)
      HttpGet request = new HttpGet(url); // (3)
      response = http_client.execute(request); // (4)
      int code = response.getStatusLine().getStatusCode(); // (5)

      if(code==HttpStatus.SC_OK)
      {
        HttpEntity entity = response.getEntity(); // (6)
        String json = EntityUtils.toString(entity); // (7)
        Gson gson = new Gson();
        tokens = gson.fromJson(json, OneDriveToken.class); // (8)
        tokens.setTime(System.currentTimeMillis()); // このアクセストークンを受け取った時刻を記録。
        return true;
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      try{if(response   !=null) response.close();}   catch(Exception e){e.printStackTrace();}
      try{if(http_client!=null) http_client.close();}catch(Exception e){e.printStackTrace();}
    }

    return false;
  }

  /**
   * 第6章：クライアントシークレットをセットする。
   */
  public void setClientSecret(String client_secret)
  {
    this.client_secret = client_secret;
  }

  /**
   * 第6章：クライアントシークレットを参照する。
   */
  public String getClientSecret()
  {
    return this.client_secret;
  }

  /**
   * 第6章：アクセストークンの文字列を参照。
   * もし、有効期限を過ぎていた場合は、リフレッシュトークンを使ってアクセストークンを再度取得する。
   */
  public String getAccessToken()
  {
    if(tokens==null)
    {
      return null;
    }
    else if(tokens.isAvailable()==true) // 第7章：アクセストークンは有効期限内。
    {
      return tokens.access_token;
    }
    else // 第7章：アクセストークンの有効期限が切れた。
    {
      if(requestAccessTokenByRefreshToken()==true) // 第7章：リフレッシュトークンを使ってアクセストークンを再取得。
      {
        return tokens.access_token;
      }
    }

    return null;
  }

  /**
   * 第6章：リフレッシュトークンの文字列を参照。
   */
  public String getRefreshToken()
  {
    return (tokens!=null ? tokens.refresh_token : null);
  }

  /**
   * 第6章：サインインしているかどうかを返す。
   */
  public boolean isSignedIn()
  {
    if(getRefreshToken()!=null)
    {
      return true; // リフレッシュトークンがあるということはサインインしているということ。
    }

    return false;
  }

  /**
   * 第6章：アクセストークンの文字列などのクライアントの各情報をファイルに保存する。
   */
  public static boolean save(OneDrive client, File file, byte[] key)
  {
    try
    {
      if(file.getParentFile().exists()==false) // ディレクトリが存在していない場合。
      {
        file.mkdirs(); // ディレクトリを生成。
      }

      SecretKeySpec spec = new SecretKeySpec(key, "Blowfish"); // 暗号化キー。
      Cipher cipher = Cipher.getInstance("Blowfish"); // 暗号化処理クラスのインスタンスを取得。
      cipher.init(Cipher.ENCRYPT_MODE, spec); // 暗号化処理の初期化。
      ObjectOutputStream stream = new ObjectOutputStream(new CipherOutputStream(new FileOutputStream(file), cipher));
      stream.writeObject(client);
      stream.close();
      return true;
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }

    return false;
  }

  /**
   * 第6章：アクセストークンの文字列などのクライアントの各情報をファイルから読み込む。
   */
  public static OneDrive load(File file, byte[] key)
  {
    try
    {
      if(file.exists()==true)
      {
        SecretKeySpec spec = new SecretKeySpec(key, "Blowfish"); // 暗号化キー。
        Cipher cipher = Cipher.getInstance("Blowfish"); // 暗号化処理クラスのインスタンスを取得。
        cipher.init(Cipher.DECRYPT_MODE, spec); // 暗号化処理の初期化。
        ObjectInputStream stream = new ObjectInputStream(new CipherInputStream(new FileInputStream(file), cipher));
        OneDrive client = (OneDrive)stream.readObject();
        stream.close();
        return client;
      }
    }
    catch (Exception e)
    {
      System.out.println(file.getAbsolutePath() + "を読み込めませんでした。");
    }

    return null;
  }

  /**
   * 第7章：ルートフォルダの情報とルートフォルダ内のアイテム一覧を返す。
   */
  public OneDriveItem getRootFolder()
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API
          + "/drive/items/root"
          + "?expand=children"
          + "&access_token=" + getAccessToken();

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url);
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_OK)
      {
        HttpEntity entity = response.getEntity();
        String json = EntityUtils.toString(entity);
        Gson gson = new Gson();
        OneDriveItem item = gson.fromJson(json, OneDriveItem.class);

        if(item!=null) // 第8章：取得したアイテムをキャッシュに保管する。
        {
          putCachedItem(item);
          putCachedItem(item.children);
        }

        return item;
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      try{if(response   !=null) response.close();}   catch(Exception e){e.printStackTrace();}
      try{if(http_client!=null) http_client.close();}catch(Exception e){e.printStackTrace();}
    }

    return null;
  }

  /**
   * 8章：指定したフォルダ内にあるアイテムの一覧を返す。
   */
  public List<OneDriveItem> getFolder(OneDriveItem item_folder)
  {
    CloseableHttpClient http_client = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "/drive/items/" + item_folder.getID();
      }
      else // パスで指定するモード。
      {
        url += URLEncoder.encode(item_folder.getPath(), "UTF-8") + ":";
      }

      url += "/children";
      url += "?access_token=" + getAccessToken();

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();

      ArrayList<OneDriveItem> list = new ArrayList<OneDriveItem>();

      while(url!=null)
      {
        HttpGet request = new HttpGet(url);
        url = null;
        CloseableHttpResponse response = http_client.execute(request);

        try
        {
          int code = response.getStatusLine().getStatusCode();

          if(code==HttpStatus.SC_OK)
          {
            HttpEntity entity = response.getEntity();
            String json = EntityUtils.toString(entity);
            Gson gson = new Gson();
            OneDriveItemList item_list = gson.fromJson(json, OneDriveItemList.class);

            if(item_list!=null)
            {
              if(item_list.value!=null && item_list.value.size()>0)
              {
                list.addAll(item_list.value);
              }

              if(item_list.odata_nextLink!=null) // 続きがある。
              {
                url = item_list.odata_nextLink; // 続きはこの URLから取得する。
              }
              else
              {
                putCachedItem(list); // キャッシュにアイテムの情報を蓄える。
                return list;
              }
            }
            else
            {
              return null;
            }
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          try{if(response!=null) response.close();}catch(Exception e){e.printStackTrace();}
        }
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      try{if(http_client!=null) http_client.close();}catch(Exception e){e.printStackTrace();}
    }

    return null;
  }

  /**
   * 第8章：APIモードを返す。
   * MODE_API_ID   : IDを指定するタイプのAPI
   * MODE_API_PATH : パスを指定するタイプのAPI
   */
  public void setApiMode(int mode)
  {
    if(mode==MODE_API_ID || mode==MODE_API_PATH)
    {
      this.mode = mode;
    }
    else
    {
      throw new RuntimeException("間違ったパラメータ : " + mode);
    }
  }

  /**
   * 第8章：APIモードを返す。
   * MODE_API_ID   : IDを指定するタイプのAPI
   * MODE_API_PATH : パスを指定するタイプのAPI
   */
  public int getApiMode()
  {
    return mode;
  }

  /**
   * 第8章：指定したアイテムをキャッシュに保管する。
   */
  private synchronized void putCachedItem(OneDriveItem item)
  {
    if(item!=null)
    {
      if(map_items==null)
      {
        map_items = new HashMap<String, OneDriveItem>();
      }

      map_items.put(item.id, item);
    }
  }

  /**
   * 第8章：指定したアイテムをキャッシュに保管する。
   */
  private void putCachedItem(List<OneDriveItem> list)
  {
    if(list!=null)
    {
      for(OneDriveItem item : list)
      {
        if(item.deleted==null)
        {
          putCachedItem(item);
        }
        else // 第21章：削除されたアイテムの場合。
        {
          removeCachedItem(item); // キャッシュから削除。
        }
      }
    }
  }

  /**
   * 第8章：IDを指定してローカルで保管しているアイテムを返す。
   */
  public synchronized OneDriveItem getCachedItem(String id)
  {
    if(map_items!=null && id!=null)
    {
      return map_items.get(id);
    }

    return null;
  }

  /**
   * 第9章：アイテムのメタデータを取得。
   */
  public OneDriveItem getItemMetaData(OneDriveItem item)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "drive/items/" + item.id;
      }
      else // パスで指定するモード。
      {
        url += URLEncoder.encode(item.getPath(), "UTF-8") + ":";
      }

      url += "?access_token=" + getAccessToken();

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url);
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_OK)
      {
        HttpEntity entity = response.getEntity();
        String json = EntityUtils.toString(entity);
        Gson gson = new Gson();
        OneDriveItem item_received = gson.fromJson(json, OneDriveItem.class);

        if(item_received!=null)
        {
          putCachedItem(item_received); // キャッシュに情報を蓄える。
        }

        return item_received;
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

    return null;
  }

  /**
   * 第10章：ファイルをダウンロードする。
   */
  public boolean downloadFile(OneDriveItem item, File file_local)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "/drive/items/" + item.id;
      }
      else if(mode==MODE_API_PATH) // パスで指定するモード。
      {
        url += URLEncoder.encode(item.getPath(), "UTF-8") + ":";
      }

      url += "/content";
      url += "?access_token=" + getAccessToken();

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url);
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_OK)
      {
        Header header = response.getFirstHeader("Content-Location");
        URL url_download = new URL(header.getValue()); // ダウンロード用のURLを表す文字列がヘッダに格納されている。
        return downloadFile(url_download, file_local);
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

    return false;
  }

  /**
   * 第10章：URLを指定してファイルをダウンロードする。
   */
  private boolean downloadFile(URL url, File file_local)
  {
    InputStream stream_in   = null;
    OutputStream stream_out = null;

    try
    {
      URLConnection connection = url.openConnection();
      stream_in = new BufferedInputStream(connection.getInputStream());
      stream_out = new BufferedOutputStream(new FileOutputStream(file_local, false));
      byte[] buffer = new byte[1024*1024];

      while(true)
      {
        int size = stream_in.read(buffer);

        if(size>0)
        {
          stream_out.write(buffer, 0, size);
        }
        else
        {
          break;
        }
      }

      return true;
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      try{stream_in.close() ;}catch (Exception e){e.printStackTrace();}
      try{stream_out.close();}catch (Exception e){e.printStackTrace();}
    }

    return false;
  }

  /**
   * 第11章：ファイルのダウンローダーを生成してリストに格納する。
   */
  public OneDriveDownloader createDownloader(OneDriveItem item, File file_local)
  {
    OneDriveDownloader downloader = new OneDriveDownloader(item, file_local, this);

    if(list_downloader==null)
    {
      list_downloader = new ArrayList<OneDriveDownloader>();
    }

    list_downloader.add(downloader);

    return downloader;
  }

  /**
   * 第11章：リストに保持しているダウンローダーの数を返す。
   */
  public int getDownloaderSize()
  {
    return (list_downloader!=null ? list_downloader.size() : 0);
  }

  /**
   * 第11章：リストに保持しているダウンローダーを返す。
   */
  public OneDriveDownloader getDownloader(int index)
  {
    return (list_downloader!=null ? list_downloader.get(index) : null);
  }

  /**
   * 第11章：リストに保持しているダウンローダーを削除する。
   */
  public void removeDownloader(OneDriveDownloader downloader)
  {
    if(list_downloader!=null)
    {
      list_downloader.remove(downloader);
    }
  }

  /**
   * 第12章：指定したパスのフォルダーを作成する。
   */
  public OneDriveItem createFolder(OneDriveItem item_parent, String name)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      String url = URL_ONEDRIVE_API;

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "/drive/items/" + item_parent.id;
      }
      else if(mode==MODE_API_PATH) // パスで指定するモード。
      {
        url += URLEncoder.encode(item_parent.getPath(), "UTF-8") + ":";
      }

      url += "/children";
      url += "?access_token=" + getAccessToken();

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      OneDriveCreateFolder param = new OneDriveCreateFolder(name, "fail");
      HttpPost request = new HttpPost(url);
      Gson gson = new Gson();
      String json_send = gson.toJson(param);
      request.addHeader("Content-type", "application/json");
      request.setEntity(new StringEntity(json_send, "UTF-8"));
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_CREATED)
      {
        HttpEntity entity = response.getEntity();
        String json_received = EntityUtils.toString(entity);
        OneDriveItem item = gson.fromJson(json_received, OneDriveItem.class);

        if(item!=null)
        {
          putCachedItem(item);
        }

        return item;
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

    return null;
  }

  /**
   * 第12章：フォルダの新規作成のパラメータ送信するために使うJSONオブジェクトの「器」。
   */
  private class OneDriveCreateFolder
  {
    public String name;
    public JsonObject folder = new JsonObject(); // 空の JsonObject
    @SerializedName("@name.conflictBehavior")
    public String name_conflictBehavior = "fail"; // "rename" か "replace" か "fail" を指定してください。

    public OneDriveCreateFolder(String name, String name_conflictBehavior)
    {
      this.name = name;
      this.name_conflictBehavior = name_conflictBehavior;
    }
  }

  /**
   * 第12章：アプリの専用フォルダを参照する（存在していなければ作成する）。
   */
  public OneDriveItem getAppFolder()
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API
         + "/drive/special/approot:"
         + "?access_token=" + getAccessToken();

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url);
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_OK)
      {
        HttpEntity entity = response.getEntity();
        String json = EntityUtils.toString(entity);
        Gson gson = new Gson();
        OneDriveItem item = gson.fromJson(json, OneDriveItem.class);

        if(item!=null)
        {
          putCachedItem(item);
        }

        return item;
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

    return null;
  }

  /**
   * 第13章：ファイルのアップロード。
   */
  public OneDriveItem uploadFile(OneDriveItem item_folder, File file_local)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;
      String name = file_local.getName();

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "/drive/items/" + item_folder.id + "/children/";
        url += URLEncoder.encode(name, "UTF-8") + "/content";
      }
      else if(mode==MODE_API_PATH) // パスで指定するモード。
      {
        url += URLEncoder.encode(item_folder.getPath() + "/" + name, "UTF-8");
        url += ":/content";
      }

      url += "?@name.conflictBehavior=rename";  // "rename" か "replace" か "fail" を指定してください。
      url += "&access_token=" + getAccessToken();

      // アップロードするファイルをバイト配列に格納する。

      long length = file_local.length();

      if(length<=0 || 100*1024*1024<length) // 100MBまで送信可能。
      {
        return null;
      }

      byte[] buffer = new byte[(int)length];

      length = this.getByteArrayFromFile(buffer, file_local, 0, (int)length); // バイト列にファイルの中身を入れる。

      if(length<=0)
      {
        return null;
      }

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpPut request = new HttpPut(url);
      ByteArrayEntity entry = new ByteArrayEntity(buffer);
      request.setEntity(entry);
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_CREATED || // ファイルが新規作成された。
         code==HttpStatus.SC_OK)        // 既存のファイルが上書きされた。
      {
        HttpEntity entity = response.getEntity();
        String json = EntityUtils.toString(entity);
        Gson gson = new Gson();
        OneDriveItem item = gson.fromJson(json, OneDriveItem.class);
        putCachedItem(item);
        return item;
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

    return null;
  }

  /**
   * 第13章：ローカルにあるファイルの指定した範囲をバイト列に変換する。
   */
  public int getByteArrayFromFile(byte[] buff, File file, long start, int length)
  {
    FileInputStream stream = null;

    try
    {
      stream = new FileInputStream(file);
      stream.skip(start);
      return stream.read(buff, 0, length);
    }
    catch(Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      try
      {
        if(stream!=null) stream.close();
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
    }

    return -1;
  }

  /**
   * 第14章：ファイルのアップローダーを生成してリストに格納する。
   */
  public OneDriveUploader createUploader(OneDriveItem item, File file_local)
  {
    if(list_uploader==null)
    {
      list_uploader = new ArrayList<OneDriveUploader>();
    }

    OneDriveUploader uploader = new OneDriveUploader(item, file_local, this);
    list_uploader.add(uploader);

    return uploader;
  }

  /**
   * 第14章：リストに保持しているアップローダーの数を返す。
   */
  public int getUploaderSize()
  {
    return (list_uploader!=null ? list_uploader.size() : 0);
  }

  /**
   * 第14章：リストに保持しているアップローダーを返す。
   */
  public OneDriveUploader getUploader(int index)
  {
    return (list_uploader!=null ? list_uploader.get(index) : null);
  }

  /**
   * 第14章：リストに保持しているアップローダーを削除する。
   */
  public void removeUploader(OneDriveUploader uploader)
  {
    if(list_uploader!=null)
    {
      list_uploader.remove(uploader);
    }
  }

  /**
   * 第15章：インターネット上の指定したURLからファイルの OneDriveへアップロードする。
   */
  public String uploadFileFromUrl(OneDriveItem item_folder, String url_source, String name)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;
      url += "/drive/items/" + item_folder.id + "/children";
      url += "?@name.conflictBehavior=rename";
      url += "&access_token=" + getAccessToken();

      http_client = HttpClientBuilder.create().build();
      HttpPost request = new HttpPost(url);
      OneDriveUploadFileFromUrl param = new OneDriveUploadFileFromUrl(url_source, name);
      Gson gson = new Gson();
      String json_send = gson.toJson(param);
      request.addHeader("Prefer", "respond-async");
      request.addHeader("Content-type", "application/json");
      request.setEntity(new StringEntity(json_send, "UTF-8"));
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_ACCEPTED) // URLからのファイルのアップロードがサーバーに受理された。
      {
        Header[] header = response.getHeaders("Location");
        String url_monitor = (header!=null && header.length>0 ? header[0].getValue() : null);

        if(url_monitor!=null)
        {
          int index_access_token = url_monitor.indexOf("access_token=");

          if(index_access_token>0)
          {
            url_monitor = url_monitor.substring(0, index_access_token); // URLに含まれているアクセストークンを分離する。
          }
        }

        return url_monitor;
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

    return null;
  }

  /**
   * 第15章：URLからのアップロードのパラメータをサーバーに送るためのJSONオブジェクトの「器」
   */
  private class OneDriveUploadFileFromUrl
  {
    String name;
    JsonObject file = new JsonObject(); // 空の JsonObject
    @SerializedName("@content.sourceUrl")
    String content_sourceUrl;

    public OneDriveUploadFileFromUrl(String content_sourceUrl, String name)
    {
      this.content_sourceUrl = content_sourceUrl;
      this.name = name;
    }
  }

  /**
   * 第15章：OneDrive上の処理の進捗状況をモニタリングする。
   */
  public Object monitorProgress(String url_monitor)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      http_client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url_monitor + "access_token=" + this.getAccessToken());
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_ACCEPTED || code==HttpStatus.SC_OK)
      {
        HttpEntity entity = response.getEntity();
        String json = EntityUtils.toString(entity);
        Gson gson = new Gson();

        if(code==HttpStatus.SC_ACCEPTED) // URLからのファイルのアップロードが進行中。
        {
          OneDriveProgressMonitor progress = gson.fromJson(json, OneDriveProgressMonitor.class);
          return progress;
        }
        else if(code==HttpStatus.SC_OK) // URLからのファイルのアップロードが完了した。
        {
          OneDriveItem item = gson.fromJson(json, OneDriveItem.class);
          return item;
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

    return null;
  }

  /**
   * 第16章：アイテムのメタデータを更新する。
   */
  public OneDriveItem setItemMetaData(OneDriveItem item,
      OneDriveItem item_parent,
      String name,
      String description,
      Date created,
      Date modified)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "/drive/items/" + item.id;
      }
      else if(mode==MODE_API_PATH) // パスで指定するモード。
      {
        url += URLEncoder.encode(item.getPath(), "UTF-8");
      }

      url += "?access_token=" + getAccessToken();

      // 変更するメタデータをセット。

      OneDriveItemMetaDataToWrite param = new OneDriveItemMetaDataToWrite();

      if(name!=null)        param.setName(name);
      if(item_parent!=null) param.setParent(item_parent);
      if(description!=null) param.setDescription(description);
      if(created!=null)     param.setCreatedDateTime(created);
      if(modified!=null)    param.setLastModifiedDateTime(modified);

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpPatch request = new HttpPatch(url);
       Gson gson = new Gson();
      String json_send = gson.toJson(param);
      request.addHeader("Content-type", "application/json");
      request.setEntity(new StringEntity(json_send, "UTF-8"));
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_OK)
      {
        HttpEntity entity = response.getEntity();
        String json_received = EntityUtils.toString(entity);
        OneDriveItem item_received = gson.fromJson(json_received, OneDriveItem.class);

        if(item_received!=null)
        {
          putCachedItem(item_received);
          return item_received;
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

    return null;
  }

  /**
   * 第16章：メタデータの更新情報をサーバーに送信するために使うJSONオブジェクトの「器」。
   */
  private class OneDriveItemMetaDataToWrite
  {
    OneDriveItemReference parentReference;
    String name;
    String description;
    OneDriveFileSystemInfo fileSystemInfo;

    public void setName(String name)
    {
      this.name = name;
    }

    public void setParent(OneDriveItem parent)
    {
      this.parentReference = new OneDriveItemReference();

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        this.parentReference.id = parent.id;
      }
      else if(mode==MODE_API_PATH) // パスで指定するモード。
      {
        this.parentReference.path = parent.getPath();
      }
    }

    public void setDescription(String description)
    {
      this.description = description;
     }

    public void setCreatedDateTime(Date date)
    {
      if(this.fileSystemInfo==null)
      {
        this.fileSystemInfo = new OneDriveFileSystemInfo();
      }

      SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      date_format.format(date);
      this.fileSystemInfo.createdDateTime = date_format.format(date);
    }

    public void setLastModifiedDateTime(Date date)
    {
      if(this.fileSystemInfo==null)
      {
        this.fileSystemInfo = new OneDriveFileSystemInfo();
      }

      SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      date_format.format(date);
      this.fileSystemInfo.lastModifiedDateTime = date_format.format(date);
    }
  }

//  /**
//  * 第16章：HttpPatch は Appatch HttpClient のバージョン4.2以降には含まれていますが、
//  * アンドロイド環境では使用することができませんので、代わりに下記のコードを使用することも可能です。
//  */
//  public class HttpPatch extends HttpPost
//  {
//   public HttpPatch(String url)
//   {
//     super(url);
//   }
//
//   @Override
//   public String getMethod()
//   {
//     return "PATCH";
//   }
//  }

  /**
   * 第16章：アイテムを指定したフォルダに移動する。
   */
  public OneDriveItem moveItem(OneDriveItem item, OneDriveItem item_folder)
  {
    return setItemMetaData(item, item_folder, null, null, null, null);
  }

  /**
   * 第16章：アイテムの名前を変更する。
   */
  public OneDriveItem renameItem(OneDriveItem item, String name)
  {
    return setItemMetaData(item, null, name, null, null, null);
  }

  /**
   * 第16章：アイテムに説明をセットする。
   */
  public OneDriveItem setDescription(OneDriveItem item, String description)
  {
    return setItemMetaData(item, null, null, description, null, null);
  }

  /**
   * 第16章：アイテムにローカルファイルの作成日時、最終更新日時をセットする。
   */
  public OneDriveItem setFileSystemInfo(OneDriveItem item, Date created, Date modified)
  {
    return setItemMetaData(item, null, null, null, created, modified);
  }

  /**
   * 第16章：ローカルでキャッシュしているフォルダをリストアップする。
   */
  public OneDriveItem[] getCachedFolders()
  {
    ArrayList<OneDriveItem> list = new ArrayList<OneDriveItem>();

    Collection<OneDriveItem> collection = map_items.values();

    if(collection!=null)
    {
      for(OneDriveItem item : collection)
      {
        if(item.isFolder()==true)
        {
          list.add(item);
        }
      }
    }

    return list.toArray(new OneDriveItem[0]);
  }

  /**
   * 第17章：アイテムを指定したフォルダにコピーする。
   */
  public String copyItem(OneDriveItem item, OneDriveItem item_parent, String name)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "/drive/items/" + item.id +"/action.copy";
      }
      else if(mode==MODE_API_PATH) // パスで指定するモード。
      {
        url += URLEncoder.encode(item.getPath(), "UTF-8") + ":/action.copy";
      }

      url += "?access_token=" + getAccessToken();

      // コピー後のアイテムのメタデータをセット。

      OneDriveItemMetaDataToWrite param = new OneDriveItemMetaDataToWrite();
      if(name!=null)        param.setName(name);
      if(item_parent!=null) param.setParent(item_parent);

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpPost request = new HttpPost(url);
      Gson gson = new Gson();
      String json_send = gson.toJson(param);
      request.addHeader("Content-type", "application/json");
      request.addHeader("Prefer", "respond-async");
      request.setEntity(new StringEntity(json_send, "UTF-8"));
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_ACCEPTED)
      {
        Header[] header = response.getHeaders("Location");
        String url_monitor = (header!=null && header.length>0 ? header[0].getValue() : null);

        if(url_monitor!=null)
        {
          int index_access_token = url_monitor.indexOf("access_token=");

          if(index_access_token>0)
          {
            url_monitor = url_monitor.substring(0, index_access_token); // URLに含まれているアクセストークンを分離する。
          }
        }

        return url_monitor;
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

    return null;
  }

  /**
   * 第17章：アイテムの削除。
   */
  public boolean deleteItem(OneDriveItem item)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "/drive/items/" + item.id;
      }
      else if(mode==MODE_API_PATH) // パスで指定するモード。
      {
        url += URLEncoder.encode(item.getPath(), "UTF-8");
      }

      url += "?access_token=" + getAccessToken();

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpDelete request = new HttpDelete(url);
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_NO_CONTENT)
      {
        removeCachedItem(item);
        removeChachedThumbnailImage(item); // 第18章：サムネイル画像のキャッシュも削除する。
        return true;
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

    return false;
  }

  /**
   * 第17章：IDを指定してローカルでキャッシュしているアイテムを削除する。
   */
  private void removeCachedItem(OneDriveItem item)
  {
    if(map_items!=null)
    {
      map_items.remove(item.id);
    }
  }

  /**
   * 第18章：サムネイル画像を取得する。
   */
  public byte[] getThumbnailImage(OneDriveItem item)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "/drive/items/" + item.id + "/thumbnails";
      }
      else if(mode==MODE_API_PATH) // パスで指定するモード。
      {
        url += URLEncoder.encode(item.getPath(), "UTF-8") + ":/thumbnails";
      }

      url += "?access_token=" + getAccessToken();

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url);
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_OK)
      {
        HttpEntity entity = response.getEntity();
        String json = EntityUtils.toString(entity);
        Gson gson = new Gson();
        OneDriveThumbnailSetList list = gson.fromJson(json, OneDriveThumbnailSetList.class);

        if(list!=null && list.value!=null)
        {
          if(list.value.size()>0)
          {
            OneDriveThumbnailSet set = list.value.get(0);

            try
            {
              byte[] image = downloadFile(new URL(set.medium.url));

              if(image!=null)
              {
                putCachedThumbnailImage(item, image);
              }

              return image;
            }
            catch (Exception e)
            {
              e.printStackTrace();
            }
          }
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

    return null;
  }

  /**
   * 第18章：サムネイル画像を取得するためのJSONオブジェクトの「器」
   */
  public class OneDriveThumbnailSetList
  {
    List<OneDriveThumbnailSet> value;
  }

  /**
   * 第18章：サムネイル画像を取得するためのJSONオブジェクトの「器」
   */
  public class OneDriveThumbnailSet
  {
    String id;
    OneDriveThumbnail small;
    OneDriveThumbnail medium;
    OneDriveThumbnail large;
  }

  /**
   * 第18章：サムネイル画像を取得するためのJSONオブジェクトの「器」
   */
  public class OneDriveThumbnail
  {
    int width;
    int height;
    String url;
  }

  /**
   * 第18章：URLを指定してファイルを byte[] にダウンロードする。
   */
  private byte[] downloadFile(URL url)
  {
    InputStream stream_in   = null;
    ByteArrayOutputStream stream_out = null;

    try
    {
      URLConnection connection = url.openConnection();
      stream_in = new BufferedInputStream(connection.getInputStream());
      stream_out = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024*1024];

      while(true)
      {
        int size = stream_in.read(buffer);

        if(size>0)
        {
          stream_out.write(buffer, 0, size);
        }
        else
        {
          break;
        }
      }

      return stream_out.toByteArray();
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      try{stream_in.close() ;}catch (Exception e){e.printStackTrace();}
      try{stream_out.close();}catch (Exception e){e.printStackTrace();}
    }

    return null;
  }

  /**
   * 第18章：サムネイル画像をローカルでキャッシュする。
   */
  private synchronized void putCachedThumbnailImage(OneDriveItem item, byte[] image)
  {
    if(item!=null && item.id!=null)
    {
      if(map_thumbnails==null)
      {
        map_thumbnails = new HashMap<String, byte[]>();
      }

      map_thumbnails.put(item.id, image);
    }
  }

  /**
   * 第18章：ローカルでキャッシュしているサムネイル画像を返す。
   */
  public synchronized byte[] getChachedThumbnailImage(OneDriveItem item)
  {
    if(item!=null && item.id!=null)
    {
      if(map_thumbnails!=null)
      {
        return map_thumbnails.get(item.id);
      }
    }

    return null;
  }

  /**
   * 第18章：ローカルでキャッシュしているサムネイル画像を削除する。
   */
  public synchronized byte[] removeChachedThumbnailImage(OneDriveItem item)
  {
    if(item!=null && item.id!=null)
    {
      if(map_thumbnails!=null)
      {
        return map_thumbnails.remove(item.id);
      }
    }

    return null;
  }

  /**
   * 第18章：カスタムサムネイル画像をセットする。
   */
  public boolean setThumbnailImage(OneDriveItem item, File file)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "/drive/items/" + item.id + "/thumbnails";
      }
      else if(mode==MODE_API_PATH) // パスで指定するモード。
      {
        url += URLEncoder.encode(item.getPath(), "UTF-8") + ":/thumbnails";
      }

      url += "/0/source/content";
      url += "?access_token=" + getAccessToken();

      // サムネイル画像

      int index_dot = file.getName().lastIndexOf(".");

      if(index_dot<0)
      {
        return false;
      }

      String ext = file.getName().substring(index_dot);

      if(ext==null)
      {
        return false;
      }

      String content_type = null;
      if(ext.compareToIgnoreCase(".BMP")==0)  content_type = "image/x-bmp";
      if(ext.compareToIgnoreCase(".GIF")==0)  content_type = "image/gif";
      if(ext.compareToIgnoreCase(".JPG")==0)  content_type = "image/jpeg";
      if(ext.compareToIgnoreCase(".JPEG")==0) content_type = "image/jpeg";
      if(ext.compareToIgnoreCase(".PNG")==0)  content_type = "image/png";

      if(content_type==null)
      {
        return false;
      }

      byte[] image = new byte[(int)file.length()];

      if(getByteArrayFromFile(image, file, 0, (int)file.length())!=file.length())
      {
        return false;
      }

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpPut request = new HttpPut(url);
      request.addHeader("Content-type", content_type);
      request.setEntity(new ByteArrayEntity(image));
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_OK)
      {
        putCachedThumbnailImage(item, image); // アップロードしたサムネイル画像をキャッシュに格納する。
        return true;
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

    return false;
  }

  /**
   * 第19章：アイテム共有用のリンクを作成する。
   */
  public String createSharingLink(OneDriveItem item, String type)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API;

      if(mode==MODE_API_ID) // IDで指定するモード。
      {
        url += "/drive/items/" + item.id;
      }
      else if(mode==MODE_API_PATH) // パスで指定するモード。
      {
        url += URLEncoder.encode(item.getPath(), "UTF-8") + ":";
      }

      url += "/action.createLink";
      url += "?access_token=" + getAccessToken();

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();
      HttpPost request = new HttpPost(url);
      OneDriveCreateSharingLink param = new OneDriveCreateSharingLink(type);
      Gson gson = new Gson();
      String json_send = gson.toJson(param);
      request.addHeader("Content-type", "application/json");
      request.setEntity(new StringEntity(json_send, "UTF-8"));
      response = http_client.execute(request);
      int code = response.getStatusLine().getStatusCode();

      if(code==HttpStatus.SC_CREATED || // 共有リンクが新規作成された。
         code==HttpStatus.SC_OK)        // 既存の共有リンクが渡されてきた。
      {
        HttpEntity entity = response.getEntity();
        String json_received = EntityUtils.toString(entity);
        OneDrivePermission permission = gson.fromJson(json_received, OneDrivePermission.class);
        return permission.link.webUrl;
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

    return null;
  }

  /**
   * 第19章：ファイル共有のリンクを作成するためのJSONオブジェクトの「器」
   */
  private class OneDriveCreateSharingLink
  {
    String type;

    public OneDriveCreateSharingLink(String type)
    {
      this.type = type;
    }
  }

  /**
   * 第19章：ファイル共有のリンクを取得するためのJSONオブジェクトの「器」
   */
  private class OneDrivePermission
  {
    public String id;
    public List<String> roles;
    public OneDriveSharingLink link;
  }

  /**
   * 第19章：ファイル共有のリンクを取得するためのJSONオブジェクトの「器」
   */
  private class OneDriveSharingLink
  {
    public String token;
    public String webUrl;
    public String type;
    public OneDriveIdentity application;
  }

  /**
   * 第20章：アイテムの検索。
   */
  public List<OneDriveItem> searchItems(String keywords)
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API
          + "/drive/root/view.search"
          + "?q=" + URLEncoder.encode(keywords, "UTF-8")
          + "&access_token=" + getAccessToken();

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();

      ArrayList<OneDriveItem> list = new ArrayList<OneDriveItem>();

      while(url!=null)
      {
        HttpGet request = new HttpGet(url);
        url = null;
        response = http_client.execute(request);

        try
        {
          int code = response.getStatusLine().getStatusCode();

          if(code==HttpStatus.SC_OK)
          {
            HttpEntity entity = response.getEntity();
            String json = EntityUtils.toString(entity);
            Gson gson = new Gson();
            OneDriveItemList item_list = gson.fromJson(json, OneDriveItemList.class);

            if(item_list!=null)
            {
              if(item_list.value!=null && item_list.value.size()>0) // 変更されたアイテムの情報が存在する。
              {
                list.addAll(item_list.value);
              }

              if(item_list.odata_nextLink!=null) // 続きがある。
              {
                url = item_list.odata_nextLink; // 続きはこの URLから取得する。
              }
              else
              {
                putCachedItem(list); // キャッシュを更新。
                return list;
              }
            }
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          try{if(response!=null) response.close();}catch(Exception e){e.printStackTrace();}
        }
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      try{if(http_client!=null) http_client.close();}catch (Exception e){e.printStackTrace();}
    }

    return null;
  }

  /**
   * 第21章：最近の変更を調べる。
   */
  public List<OneDriveItem> getChanges()
  {
    CloseableHttpClient http_client = null;
    CloseableHttpResponse response = null;

    try
    {
      // REST API の URLを生成。

      String url = URL_ONEDRIVE_API
          + "/drive/root/view.delta"
          + "?access_token=" + getAccessToken();

      if(delta_token!=null)
      {
        url += "&token=" + delta_token; // 前回、変更を取得したときのトークン。
      }

      // HTTP通信を行う。

      http_client = HttpClientBuilder.create().build();

      ArrayList<OneDriveItem> list = new ArrayList<OneDriveItem>();

      while(url!=null)
      {
        HttpGet request = new HttpGet(url);
        url = null;
        request.addHeader("Accept", "application/json");
        response = http_client.execute(request);

        try
        {
          int code = response.getStatusLine().getStatusCode();

          if(code==HttpStatus.SC_OK)
          {
            HttpEntity entity = response.getEntity();
            String json = EntityUtils.toString(entity);
            Gson gson = new Gson();
            OneDriveItemList item_list = gson.fromJson(json, OneDriveItemList.class);

            if(item_list!=null)
            {
              if(item_list.value!=null && item_list.value.size()>0) // 変更されたアイテムの情報が存在する。
              {
                list.addAll(item_list.value);
              }

              if(item_list.delta_token!=null) // 次回問い合わせのためのトークンを取得。
              {
                delta_token = item_list.delta_token;
              }

              if(item_list.odata_nextLink!=null) // まだ続きのページがある。
              {
                url = item_list.odata_nextLink; // 続きはこの URLから取得する。
              }
              else // 最後のページ
              {
                putCachedItem(list); // キャッシュを更新。
                assignParentPath(list); // 親のパスを設定しなおす。
                return list;
              }
            }
          }
          else if(code==HttpStatus.SC_GONE)
          {
            delta_token = null;
            return null;
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          try{if(response!=null) response.close();} catch(Exception e){e.printStackTrace();}
        }
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      try{if(http_client!=null) http_client.close();} catch(Exception e){e.printStackTrace();}
    }

    return null;
  }

  /**
   * 第21章：親フォルダのパスが null のアイテムがある場合、親のパスを設定する。
   */
  private void assignParentPath(List<OneDriveItem> list)
  {
    for(OneDriveItem item : list)
    {
      assignParentPath(item);
    }
  }

  /**
   * 第21章：親フォルダのパスが null のアイテムがある場合、親のパスを設定する。
   */
  private void assignParentPath(OneDriveItem item)
  {
    if(item.getParentPath()==null) // リンク切れで親フォルダのパスが取得できない。
    {
      OneDriveItem parent = getCachedItem(item.getParentID()); // 親のIDからアイテムを検索。

      if(parent!=null) // 今でも親が存在している。
      {
        if(parent.getParentPath()==null) // 親の親へのリンクが切れている（パスが設定されていない）。
        {
          assignParentPath(parent); // 再起呼び出し。親の親をセット。
        }

        item.parentReference.path = parent.getPath(); // 親のパスをセット。
      }
      else // 親が存在しない。
      {
        if(item.deleted!=null) // 削除されたアイテム。
        {
          item.parentReference.path = ""; // 削除されたアイテムの場合は、親のパスを空文字列にセット。
        }
        else // 削除されていないが、親が存在しない。→ ルートフォルダということ。
        {
          item.parentReference = null; // 親への参照をクリア。これにより isRootFolder()が true を返すようになる。
        }
      }
    }
  }
}