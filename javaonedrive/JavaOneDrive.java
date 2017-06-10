package javaonedrive;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import onedrive.OneDrive;
import onedrive.OneDriveDownloader;
import onedrive.OneDriveUploader;
import onedrive.json.OneDriveItem;
import onedrive.json.OneDriveProgressMonitor;

public class JavaOneDrive extends JFrame implements ActionListener, WindowListener
{
  private static final String CLIENT_ID     = null; // TODO ここにクライアントIDをセットしてください。
  private static final String CLIENT_SECRET = null; // TODO ここにクライアントシークレットをセットしてください。

  private static File FILE_ONE_DRIVE_DAT = new File("./OneDrive.dat"); // OneDriveクラスをシリアライズして保存するためのファイル。

  private OneDrive client;

  // [ファイル]メニュー

  private JMenuItem menu_show_root_folder = new JMenuItem("ルートフォルダを表示");
  private JMenuItem menu_refresh          = new JMenuItem("リフレッシュ");
  private JMenuItem menu_show_downloads   = new JMenuItem("ダウンロードダイアログを表示");
  private JMenuItem menu_create_foler     = new JMenuItem("フォルダの新規作成");
  private JMenuItem menu_get_app_foler    = new JMenuItem("アプリの専用フォルダを取得");
  private JMenuItem menu_upload_file      = new JMenuItem("ファイルをアップロード");
  private JMenuItem menu_show_uploads     = new JMenuItem("アップロードダイアログを表示");
  private JMenuItem menu_upload_from_url  = new JMenuItem("ファイルをインターネット上のURLからアップロード");
  private JMenuItem menu_search           = new JMenuItem("検索");
  private JMenuItem menu_get_changes      = new JMenuItem("最近の変更を取得");

  // [表示]メニュー

  private JRadioButtonMenuItem menu_view_list       = new JRadioButtonMenuItem("リスト表示");
  private JRadioButtonMenuItem menu_view_thumbnails = new JRadioButtonMenuItem("サムネイル表示");

  // [API]メニュー

  private JRadioButtonMenuItem menu_api_mode_id   = new JRadioButtonMenuItem("アイテムのIDを指定するAPIを使う");
  private JRadioButtonMenuItem menu_api_mode_path = new JRadioButtonMenuItem("アイテムのパスを指定するAPIを使う");

  // [OAuth 2.0]メニュー

  private JMenuItem menu_sign_in      = new JMenuItem("サインイン");
  private JMenuItem menu_sign_out     = new JMenuItem("サインアウト");
  private JMenuItem menu_access_token = new JMenuItem("アクセストークンの再取得");

  // アイテムを右クリックした時のポップアップメニュー

  private JPopupMenu popup = new JPopupMenu();
  private JMenuItem menu_show_meta_data  = new JMenuItem("メタデータを表示");
  private JMenuItem menu_move            = new JMenuItem("移動");
  private JMenuItem menu_rename          = new JMenuItem("名前を変更");
  private JMenuItem menu_set_description = new JMenuItem("説明を変更");
  private JMenuItem menu_copy            = new JMenuItem("コピー");
  private JMenuItem menu_delete          = new JMenuItem("削除");
  private JMenuItem menu_set_thumbnail   = new JMenuItem("サムネイル画像をセット");
  private JMenuItem menu_create_link     = new JMenuItem("共有リンクを取得");

  private CardLayout layout = new CardLayout(); // カードレイアウト
  private HashMap<JPanel, String> map_panels = new HashMap<JPanel, String>(); // カードレイアウトに指定するパネルの名前を記録するハッシュマップ。
  private JPanel panel_current; // 現在カードレイアウトによってGUI上に表示されているパネル。

  private AuthPanel panel_auth                        = new AuthPanel(this);          // OAuth認証のためのパネル
  private StatusPanel panel_status                    = new StatusPanel();            // ステータスを画面中央に表示するためのパネル
  private MessagePanel panel_message                  = new MessagePanel();           // メッセージを表示するためのパネル
  private ItemListPanel panel_folder_list             = new ItemListPanel(this);      // アイテム一覧リストの表示パネル。
  private ItemThumbnailPanel panel_folder_thumbnails  = new ItemThumbnailPanel(this); // フォルダ内のサムネイル画像。
  private MetaDataPanel panel_meta_data               = new MetaDataPanel(this);      // メタデータを表示するパネル。
  private ItemListPanel panel_search_list             = new ItemListPanel(this);      // 検索結果のリスト表示パネル。
  private ItemThumbnailPanel panel_search_thumbnails  = new ItemThumbnailPanel(this); // 検索結果のサムネイル表示パネル。

  private DownloadDialog dialog_download; // ダウンロードダイアログ。
  private UploadDialog dialog_upload;     // アップロードダイアログ。

  private OneDriveItem folder_current; // 現在GUI上に表示されているフォルダをこの変数にセットしておきます。

  private int busy_counter = 0; // このカウンターにより、何らかの処理を実行中かどうかを判定する。

  static
  {
    try // ルックアンドフィールを変更
    {
      UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  public static void main(String[] args)
  {
    // OneDrive のネットワーク処理を行うクライアントのセットアップ。

    OneDrive client = OneDrive.load(FILE_ONE_DRIVE_DAT, getEncryptKey()); // 第6章：クライアント情報を読み込む。

    if(client==null) // 第6章：クライアント情報が保存されていない。あるいは読み込めなかった。
    {
      client = new OneDrive();
    }

    if(CLIENT_ID!=null)     client.setClientID(CLIENT_ID);         // 第4章：クライアントIDをセット。
    if(CLIENT_SECRET!=null) client.setClientSecret(CLIENT_SECRET); // 第6章：クライアントシークレットをセット。

    if(client.getClientID()==null || client.getClientSecret()==null)
    {
      JOptionPane.showMessageDialog(null,
          "CLIENT_IDとCLIENT_SECRETをセットしてからコンパイル・実行してください。");
      return;
    }

    // GUIのセットアップ。

    JavaOneDrive frame = new JavaOneDrive();
    frame.setOneDriveClient(client);
    frame.setVisible(true);
  }

  /**
   * JFrameのコンストラクタ。
   */
  public JavaOneDrive()
  {
    super();

    setTitle("JavaOneDrive");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(new Dimension(800, 600));
    setLayout(layout); // 第6章：カードレイアウトをセット。

    // パネルの生成。

    panel_auth    = new AuthPanel(this); // OAuth認証用のパネル。
    panel_status  = new StatusPanel();   // ステータス表示用パネル。
    panel_message = new MessagePanel();  // メッセージ表示用パネル。

    // 第6章：カードレイアウトに登録する JPanel の名前をハッシュマップにセット。

    map_panels.put(panel_auth,              "認証パネル");
    map_panels.put(panel_status,            "ステータス表示パネル");
    map_panels.put(panel_message,           "メッセージ表示パネル");
    map_panels.put(panel_folder_list,       "フォルダ内のアイテム一覧リスト表示パネル");
    map_panels.put(panel_folder_thumbnails, "フォルダ内のアイテム一覧サムネイル表示パネル");
    map_panels.put(panel_meta_data,         "メタデータ表示パネル");
    map_panels.put(panel_search_list,       "検索結果リスト表示パネル");
    map_panels.put(panel_search_thumbnails, "検索結果サムネイル表示パネル");

    Iterator<JPanel> iter = map_panels.keySet().iterator();

    while(iter.hasNext()==true)
    {
      JPanel panel = iter.next();
      String name  = map_panels.get(panel);
      add(panel, name); // パネルに名前を関連づけてカードレイアウトに登録する。
    }

    // [ファイル]メニューをセットアップ。

    JMenu menu_file = new JMenu("ファイル");
    menu_file.add(menu_show_root_folder);
    menu_file.add(menu_refresh);
    menu_file.add(menu_show_downloads);
    menu_file.add(menu_create_foler);
    menu_file.add(menu_get_app_foler);
    menu_file.add(menu_upload_file);
    menu_file.add(menu_show_uploads);
    menu_file.add(menu_upload_from_url);
    menu_file.add(menu_search);
    menu_file.add(menu_get_changes);

    menu_show_root_folder.addActionListener(this);
    menu_refresh.addActionListener(this);
    menu_show_downloads.addActionListener(this);
    menu_create_foler.addActionListener(this);
    menu_get_app_foler.addActionListener(this);
    menu_upload_file.addActionListener(this);
    menu_show_uploads.addActionListener(this);
    menu_upload_from_url.addActionListener(this);
    menu_search.addActionListener(this);
    menu_get_changes.addActionListener(this);

    // 表示メニュー

    JMenu menu_view = new JMenu("表示");
    menu_view.add(menu_view_list);
    menu_view.add(menu_view_thumbnails);

    ButtonGroup group_view = new ButtonGroup();
    group_view.add(menu_view_list);
    group_view.add(menu_view_thumbnails);

    menu_view_list.addActionListener(this);
    menu_view_thumbnails.addActionListener(this);
    menu_view_list.setSelected(true); // 表示モードの初期化。

    // APIメニュー

    JMenu menu_api = new JMenu("APIの種類");
    menu_api.add(menu_api_mode_id);
    menu_api.add(menu_api_mode_path);

    ButtonGroup group_api = new ButtonGroup();
    group_api.add(menu_api_mode_path);
    group_api.add(menu_api_mode_id);

    menu_api_mode_id.addActionListener(this);
    menu_api_mode_path.addActionListener(this);

    // [OAuth 2.0]メニューをセットアップ。

    JMenu menu_oauth = new JMenu("OAuth 2.0");
    menu_oauth.add(menu_sign_in);
    menu_oauth.add(menu_sign_out);
    menu_oauth.add(menu_access_token);

    menu_sign_in.addActionListener(this);
    menu_sign_out.addActionListener(this);
    menu_access_token.addActionListener(this);

    // アイテムを右クリックした時のポップアップメニュー

    popup.add(menu_show_meta_data);
    popup.add(menu_move);
    popup.add(menu_rename);
    popup.add(menu_set_description);
    popup.add(menu_copy);
    popup.add(menu_delete);
    popup.add(menu_set_thumbnail);
    popup.add(menu_create_link);

    menu_show_meta_data.addActionListener(this);
    menu_move.addActionListener(this);
    menu_rename.addActionListener(this);
    menu_set_description.addActionListener(this);
    menu_copy.addActionListener(this);
    menu_delete.addActionListener(this);
    menu_set_thumbnail.addActionListener(this);
    menu_create_link.addActionListener(this);

    // JFrame にメニューバーを追加。

    JMenuBar menu_bar = new JMenuBar();
    menu_bar.add(menu_file);
    menu_bar.add(menu_view);
    menu_bar.add(menu_api);
    menu_bar.add(menu_oauth);
    setJMenuBar(menu_bar);

    // Windowsリスナーをセット。

    addWindowListener(this);
  }

  /**
   * ActionListenerの実装。
   */
  @Override
  public void actionPerformed(ActionEvent e)
  {
    // クリックされたメニューにあった処理を行う。

    Object source = e.getSource();

    // [ファイル]メニュー

    if(source==menu_show_root_folder) // ルートフォルダ内のファイル一覧を表示する。
    {
      getRootFolder();
    }
    else if(source==menu_refresh) // リフレッシュ。現在表示中のフォルダの情報を再度取得して表示。
    {
      getFolder(folder_current);
    }
    else if(source==menu_show_downloads) // ダウンロードダイアログを表示する。
    {
      showDownloadDialog();
    }
    else if(source==menu_create_foler) // フォルダの新規作成。
    {
      createFolder();
    }
    else if(source==menu_get_app_foler) // アプリの専用フォルダの取得。
    {
      getAppFolder();
    }
    else if(source==menu_upload_file) // 表示中のフォルダにファイルをアップロードする。
    {
      // uploadFile(); // 第13章
      startUploader(); // 第14章
    }
    else if(source==menu_show_uploads) // アップロードダイアログを表示する。
    {
      showUploadDialog();
    }
    else if(source==menu_upload_from_url) // インターネット上のURLからファイルをアップロードする。
    {
      uploadFileFromUrl();
    }
    else if(source==menu_search) // アイテムを検索。
    {
      searchItems();
    }
    else if(source==menu_get_changes) // 最近の変更を取得。
    {
      getChanges();
    }

    // 表示メニュー

    else if(source==menu_view_list ||      // リスト表示。
            source==menu_view_thumbnails)  // サムネイル表示。
    {
      changeViewMode(); // 第20章：表示モードの切り替えイベントを処理。
    }

    // [API]メニュー

    else if(source==menu_api_mode_id ||
            source==menu_api_mode_path)
    {
      client.setApiMode((menu_api_mode_id.isSelected()==true ? OneDrive.MODE_API_ID : OneDrive.MODE_API_PATH));
    }

    // [OAuth 2.0]メニュー

    else if(source==menu_sign_in) // サインイン
    {
      signIn();
    }
    else if(source==menu_sign_out) // サインアウト
    {
      signOut();
    }
    else if(source==menu_access_token) // アクセストークンの再取得。
    {
      requestAccessTokenByRefreshToken();
    }

    // 右クリックポップアップメニュー

    else if(source==menu_show_meta_data)
    {
      getItemMetaData();
    }
    else if(source==menu_move) // アイテムを指定したフォルダに移動する。
    {
      moveItem();
    }
    else if(source==menu_rename) // アイテムの名前を変更する。
    {
      renameItem();
    }
    else if(source==menu_set_description) // アイテムに説明をセットする。
    {
      setDescription();
    }
    else if(source==menu_copy) // アイテムを指定したフォルダにコピーする。
    {
      copyItem();
    }
    else if(source==menu_delete) // ファイルを削除。
    {
      deleteItem();
    }
    else if(source==menu_set_thumbnail) // サムネイル画像をセット。
    {
      setThumbnailImage();
    }
    else if(source==menu_create_link) // 共有リンクを取得する。
    {
      createSharingLink();
    }
  }

  /**
   * 第4章：OneDriveのクライアント情報をフレームにセット。
   */
  public void setOneDriveClient(OneDrive client)
  {
    this.client = client;

    // 第8章：APIモードの初期化。

    if(client.getApiMode()==OneDrive.MODE_API_ID)
    {
      menu_api_mode_id.setSelected(true);
    }
    else
    {
      menu_api_mode_path.setSelected(true);
    }

    // サインインの状態をチェック。

    if(client.isSignedIn()==true) // 第6章：すでにサインインしている場合。
    {
      getRootFolder(); // 第7章：ルートフォルダを表示。
    }
    else // 第6章：まだサインインしていない場合。
    {
      showStatusPanel("サインインしてください。");
    }

    // 第11章：ダウンロードの再開。

    for(int i=0;i<client.getDownloaderSize();i++)
    {
      OneDriveDownloader downloader = client.getDownloader(i);

      if(downloader.isDownloading()==true) // 前回終了時にダウンロード中のファイルがあった。
      {
        startDownloader(downloader); // ファイルの続きの部分をダウンロード。
      }
    }

    // 第14章：アップロードの再開。

    for(int i=0;i<client.getUploaderSize();i++)
    {
      OneDriveUploader uploader = client.getUploader(i);

      if(uploader.isUploading()==true)
      {
        startUploader(uploader); // ファイルの続きの部分をアップロード。
      }
    }
  }

  /**
   * 第4章：JFrame が保持している OneDriveのクライアント情報を参照する。
   */
  public OneDrive getOneDriveClient()
  {
    return this.client;
  }

  /**
   * 第5章：OAuth サインイン。
   */
  private void signIn()
  {
    if(CLIENT_ID!=null)     client.setClientID(CLIENT_ID);         // 第4章：クライアントIDをセット。
    if(CLIENT_SECRET!=null) client.setClientSecret(CLIENT_SECRET); // 第6章：クライアントシークレットをセット。

    if(client.getClientID()==null || client.getClientSecret()==null)
    {
      JOptionPane.showMessageDialog(null,
          "CLIENT_IDとCLIENT_SECRET変数をセットしてからコンパイル・実行してください。");
      return;
    }

    if(client.isSignedIn()==true)
    {
      JOptionPane.showMessageDialog(this, "すでにサインインしています。");
    }
    else
    {
      showPanel(panel_auth); // 第6章：カードレイアウトでパネルを切り替える。
      panel_auth.signIn();
    }
  }

  /**
   * 第5章：OAuth サインアウト。
   */
  private void signOut()
  {
    if(this.client.isSignedIn()==false)
    {
      JOptionPane.showMessageDialog(JavaOneDrive.this, "すでにサインアウトしています。");
      return;
    }

    int option = JOptionPane.showConfirmDialog(JavaOneDrive.this,
        "サインアウトしますか?",
        "確認",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.QUESTION_MESSAGE);

    if(option==JOptionPane.YES_OPTION)
    {
      showPanel(panel_auth); // カードレイアウトでパネルを切り替える。
      panel_auth.signOut();  // WebView のクッキーを削除。
    }
  }

  /**
   * 第6章：認証コードからアクセストークンを取得する。
   */
  protected void requestAccessTokenByCode(final String code)
  {
    // 通信中のメッセージを表示。

    showStatusPanel("サーバーからアクセストークンを取得しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<Boolean, Object> worker = new SwingWorker<Boolean, Object>()
    {
      @Override
      protected Boolean doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.requestAccessTokenByCode(code);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return false;
      }

      @Override
      protected void done()
      {
        try
        {
          boolean flag_success = get();

          if(flag_success==true)
          {
            getRootFolder(); // 第7章：アクセストークンを取得した後はルートフォルダを表示。
          }
          else
          {
            showStatusPanel("アクセストークンを取得できませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第6章：認証コードからアクセストークンを取得する。
   */
  protected void requestAccessTokenByRefreshToken()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("サーバーからアクセストークンを再取得しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<Boolean, Object> worker = new SwingWorker<Boolean, Object>()
    {
      @Override
      protected Boolean doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.requestAccessTokenByRefreshToken();
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return false;
      }

      @Override
      protected void done()
      {
        try
        {
          boolean flag_success = get();

          if(flag_success==true)
          {
            showMessagePanel("サーバーにリフレッシュトークンを送信しました。\n\n"
                + "取得したアクセストークンは下記のとおりです。\n"
                + client.getAccessToken() + "\n\n"
                + "取得したリフレッシュトークンは下記のとおりです。\n"
                + client.getRefreshToken());
          }
          else
          {
            showStatusPanel("アクセストークンを再取得できませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第6章：通信処理中かどうかのフラグをセットする。
   */
  private synchronized void setBusy(boolean flag)
  {
    if(flag==true)
    {
      busy_counter++;
    }
    else
    {
      busy_counter--;
    }
  }

  /**
   * 第6章：通信処理中かどうかを返す。
   */
  private synchronized boolean isBusy()
  {
    return (busy_counter>0);
  }

  /**
   * 第6章：指定したパネルをGUI上に表示。
   */
  public void showPanel(JPanel panel)
  {
    if(panel==panel_current) // すでにこのパネルが表示されている場合は何もしない。
    {
      return;
    }

    String name = map_panels.get(panel); // このパネルに関連付けた名前を取得。

    if(name!=null)
    {
      layout.show(getContentPane(), name); // カードレイアウトにパネルの名前を指定する。
      panel_current = panel; // 現在表示されているパネルを記憶しておく。
    }
  }

  /**
   * 第6章：GUI上にメッセージを表示する。
   */
  protected void showStatusPanel(String status)
  {
    panel_status.setStatus(status);
    showPanel(panel_status);
  }

  /**
   * 第6章：GUI上にメッセージを表示する。
   */
  protected void showMessagePanel(String message)
  {
    panel_message.setMessage(message);
    showPanel(panel_message);
  }

  /**
   * 第6章：暗号化キーを返す。
   */
  public static byte[] getEncryptKey()
  {
    String key = "KLaAqmKjZ9sAPa43ApZQ12";

    // 暗号化キーは初回起動時に生成されて、レジストリに保存されます。
    // 2回目以降の起動時はレジストリから情報を読み込みます。

    byte[] bytes = Preferences.userRoot().node(key).getByteArray(key, null);

    if(bytes==null) // 初回起動時には暗号化キーを自動で生成してレジストリに保管しておく。
    {
      try
      {
        bytes = new byte[16]; // 16バイト(128ビット)
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.nextBytes(bytes);
        Preferences.userRoot().node(key).putByteArray(key, bytes);
        return bytes;
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
    }
    else // 2回目以降の起動時には、初回起動時に生成して覚えておいた情報を返す。
    {
      return bytes;
    }

    return null;
  }

  /**
   * 第6章：WindowListenerインターフェースの実装。
   */
  @Override
  public void windowClosing(WindowEvent e)
  {
    // このメソッドは JFrame をクローズする直前に呼ばれます。

    if(this.client!=null)
    {
      OneDrive.save(this.client, FILE_ONE_DRIVE_DAT, getEncryptKey()); // クライアント情報をファイルに保存する。
    }
  }

  @Override public void windowOpened(WindowEvent e){}
  @Override public void windowIconified(WindowEvent e){}
  @Override public void windowDeiconified(WindowEvent e){}
  @Override public void windowDeactivated(WindowEvent e){}
  @Override public void windowClosed(WindowEvent e){}
  @Override public void windowActivated(WindowEvent e){}

  /**
   * 第6章：OneDriveクライアント情報のクリア。
   */
  public void clearOneDriveClient()
  {
    // OneDriveクライアントのインスタンスをクリア。

    setOneDriveClient(new OneDrive());

    // クライアント情報を保管したファイルを削除。

    if(FILE_ONE_DRIVE_DAT.exists()==true)
    {
      FILE_ONE_DRIVE_DAT.delete();
    }
  }

  /**
   * 第7章：OneDriveサーバーからルートフォルダ内のアイテム一覧を取得する。
   */
  public void getRootFolder()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("ルートフォルダのアイテム一覧を取得しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<OneDriveItem, Object> worker = new SwingWorker<OneDriveItem, Object>()
    {
      @Override
      protected OneDriveItem doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.getRootFolder();
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          OneDriveItem item = get();

          if(item!=null && item.children!=null)
          {
            showFolderPanel(item, item.children); // 取得した情報をGUI上に表示。
          }
          else
          {
            showStatusPanel("フォルダ内のアイテム一覧を取得できませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第7章：フォルダ内のアイテムのリストをGUI上に表示。
   */
  private void showFolderPanel(OneDriveItem item_folder, List<OneDriveItem> list_items)
  {
    setCurrentFolder(item_folder);
    panel_folder_list.setItems(item_folder, list_items);
    panel_folder_thumbnails.setItems(item_folder, list_items); // 第8章：サムネイル画像に対応。
    showFolderPanel();
  }

  /**
   * 第7章：フォルダ内のアイテムのリストをGUI上に表示。
   */
  public void showFolderPanel()
  {
    if(menu_view_list.isSelected()==true) // リスト表示モードの場合。
    {
      showPanel(panel_folder_list);
    }
    else // 第8章：サムネイル表示モードの場合。
    {
      showPanel(panel_folder_thumbnails);
    }
  }

  /**
   * 第7章：現在GUIに表示しているフォルダをセットする。タイトルバーにはフォルダ名も表示。
   */
  private void setCurrentFolder(OneDriveItem folder)
  {
    if(folder!=null)
    {
      setTitle("JavaOneDrive - " + folder.getPath()); // 第8章：フォルダのパスをタイトルバーに表示。
      folder_current = folder;
    }
  }

  /**
   * 第8章：OneDriveサーバーから指定したフォルダ内のアイテム一覧を取得する。
   */
  public void getFolder(final OneDriveItem folder)
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel(folder.name + " フォルダのアイテム一覧を取得しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<List<OneDriveItem>, Object> worker = new SwingWorker<List<OneDriveItem>, Object>()
    {
      @Override
      protected List<OneDriveItem> doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.getFolder(folder);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          List<OneDriveItem> list = get();

          if(list!=null)
          {
            showFolderPanel(folder, list); // 取得した情報をGUI上に表示。
          }
          else
          {
            showStatusPanel(folder.name + " フォルダ内のアイテム一覧を取得できませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第9章：右クリックポップアップメニューの表示。
   */
  public void showPopupMenu(Component invoker, int x, int y)
  {
    popup.show(invoker, x, y);
  }

  /**
   * 第9章：メタデータをGUI上にツリー表示。
   */
  public void showMetaDataPanel(Object obj)
  {
    panel_meta_data.setObject(obj);
    showPanel(panel_meta_data);
  }

  /**
   * 第9章：OneDriveサーバーからフォルダやファイルのメタデータを取得する。
   */
  public void getItemMetaData()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    OneDriveItem item = getSelectedItem();  // JTable上で選択されているアイテム。

    if(item==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("アイテム " + item.name + " のメタデータを取得しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<OneDriveItem, Object> worker = new SwingWorker<OneDriveItem, Object>()
    {
      @Override
      protected OneDriveItem doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.getItemMetaData(item);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          OneDriveItem item = get();

          if(item!=null)
          {
            showMetaDataPanel(item); // アイテムのメタデータを表示。
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "メタデータを取得できませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第9章：フォルダー表示パネル上で選択されているアイテムを返す。
   */
  private OneDriveItem getSelectedItem()
  {
    if(panel_current!=null)
    {
      if(panel_current instanceof ItemListPanel)
      {
        return ((ItemListPanel)panel_current).getSelectedItem();
      }
      else if(panel_current instanceof ItemThumbnailPanel) // 第18章：サムネイル画像の表示パネル。
      {
        return ((ItemThumbnailPanel)panel_current).getSelectedItem();
      }
    }

    return null;
  }

  /**
   * 第10章：OneDriveサーバーからファイルをダウンロードする。
   */
  public void downloadFile(OneDriveItem item)
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    File file = showFileChooserToSave(item.name);

    if(file==null)
    {
      return;
    }

    showStatusPanel("OneDrive からファイル " + item.name + " をダウンロードしています... ");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<Boolean, Object> worker = new SwingWorker<Boolean, Object>()
    {
      @Override
      protected Boolean doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.downloadFile(item, file);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return false;
      }

      @Override
      protected void done()
      {
        try
        {
          showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。

          boolean flag_success = get();

          if(flag_success==true)
          {
            int option = JOptionPane.showConfirmDialog(JavaOneDrive.this,
                file.getName() + " をダウンロードしました。\nダウンロードフォルダを開きますか?",
                "確認",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

            if(option==JOptionPane.YES_OPTION)
            {
              try
              {
                Desktop.getDesktop().open(file.getParentFile()); // ダウンロードしたファイルのフォルダをオープン。
              }
              catch (IOException e)
              {
                e.printStackTrace();
              }
            }
          }
          else
          {
            JOptionPane.showMessageDialog(JavaOneDrive.this, "ファイルをダウンロードできませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第10章:保存用ファイルチューザーを表示してユーザーにファイルを指定してもらう。
   */
  protected File showFileChooserToSave(String name)
  {
    JFileChooser chooser = new JFileChooser();

    chooser.setSelectedFile(new File(name));

    if(chooser.showSaveDialog(this)==JFileChooser.APPROVE_OPTION)
    {
      return chooser.getSelectedFile();
    }

    return null;
  }

  /**
   * 第11章:ファイルのダウンローダーを起動する。
   */
  protected void startDownloader(OneDriveItem item)
  {
    File file = showFileChooserToSave(item.name);

    if(file==null)
    {
      return;
    }

    OneDriveDownloader downloader = client.createDownloader(item, file);

    if(downloader!=null)
    {
      startDownloader(downloader);
    }
  }

  /**
   * 第11章:ファイルのダウンローダーを起動する。
   */
  public void startDownloader(OneDriveDownloader downloader)
  {
    // ダウンロードダイアログを表示。

    showDownloadDialog();

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<Integer, Object> worker = new SwingWorker<Integer, Object>()
    {
      @Override
      protected Integer doInBackground()
      {
        try
        {
          return downloader.download();
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          int status = get(); // doInBackground()の戻り値を受け取ります。

          if(status==OneDriveDownloader.STATUS_COMPLETED) // ダウンロードが完了した場合。
          {
            showDownloadDialog();
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第11章:ダウンロードダイアログを表示する
   */
  private void showDownloadDialog()
  {
    if(dialog_download==null)
    {
      dialog_download = new DownloadDialog(this, client);
    }

    dialog_download.setDownloaders();
    dialog_download.setVisible(true);
  }

  /**
   * 第12章：フォルダの新規作成。
   */
  public void createFolder()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    String name = JOptionPane.showInputDialog(this,
        "新規作成するフォルダの名前を指定してください。",
        "新しいフォルダ");

    if(name==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("フォルダ " + folder_current.name
        + " の下にサブフォルダ " + name + " を作成しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<OneDriveItem, Object> worker = new SwingWorker<OneDriveItem, Object>()
    {
      @Override
      protected OneDriveItem doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.createFolder(folder_current, name);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          OneDriveItem item = get();

          if(item!=null)
          {
            getFolder(folder_current); // 現在のフォルダ内のアイテム一覧をリフレッシュ表示。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "フォルダを作成しました。");
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "フォルダを作成できませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第12章：アプリの専用フォルダの取得（存在していなければ新規作成）。
   */
  public void getAppFolder()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("アプリの専用フォルダを取得しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<OneDriveItem, Object> worker = new SwingWorker<OneDriveItem, Object>()
    {
      @Override
      protected OneDriveItem doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.getAppFolder();
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          OneDriveItem item = get();

          if(item!=null)
          {
            getRootFolder(); // ルートフォルダを表示。その中に Appsフォルダが存在するはず。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "アプリの専用フォルダを取得しました。");
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "アプリの専用フォルダを取得できませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第13章：ファイルのアップロード。
   */
  private void uploadFile()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    File file = showFileChooserToOpen(); // ファイルチューザーを表示してローカルのファイルを選択してもらう。

    if(file==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("OneDriveサーバーへファイル " + file.getName() + " をアップロードしています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<OneDriveItem, Object> worker = new SwingWorker<OneDriveItem, Object>()
    {
      @Override
      protected OneDriveItem doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.uploadFile(folder_current, file);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          OneDriveItem item = get();

          if(item!=null)
          {
            getFolder(folder_current); // アップロード先のフォルダの表示をリフレッシュ。
            JOptionPane.showMessageDialog(JavaOneDrive.this,
                "ファイル" + file.getName() + " をアップロードしました。");
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this,
                "ファイル" + file.getName() + " をアップロードできませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第13章:読み込み用ファイルチューザーを表示してユーザーにファイルを指定してもらう。
   */
  protected File showFileChooserToOpen()
  {
    JFileChooser chooser = new JFileChooser();

    if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)
    {
      return chooser.getSelectedFile();
    }

    return null;
  }

  /**
   * 第14章：アップローダーを使ってファイルを分割アップロードする。
   */
  private void startUploader()
  {
    File file = showFileChooserToOpen(); // ファイルチューザーを表示してローカルのファイルを選択してもらう。

    if(file==null)
    {
      return;
    }

    OneDriveUploader uploader = client.createUploader(folder_current, file);

    if(uploader!=null)
    {
      startUploader(uploader);
    }
  }

  /**
   * 第14章： アップローダーを使ってファイルをアップロードする。
   */
  protected void startUploader(OneDriveUploader uploader)
  {
    // アップロードダイアログを表示。

    showUploadDialog();

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<Integer, Object> worker = new SwingWorker<Integer, Object>()
    {
      @Override
      protected Integer doInBackground()
      {
        try
        {
          return uploader.upload(client);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          showUploadDialog(); // アップロードダイアログを表示。

          int status = get(); // doInBackground()の戻り値を受け取ります。

          if(status==OneDriveUploader.STATUS_COMPLETED) // ダウンロードが完了した場合。
          {
            getFolder(folder_current); // アップロード先のフォルダの表示をリフレッシュ。
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第14章:アップロードダイアログを表示する。
   */
  private void showUploadDialog()
  {
    if(dialog_upload==null)
    {
      dialog_upload = new UploadDialog(this, client);
    }

    dialog_upload.setUploaders();
    dialog_upload.setVisible(true);
  }

  /**
   * 第15章：インターネット上のURLのファイルをOneDriveにアップロードする。
   */
  private void uploadFileFromUrl()
  {
    String url = JOptionPane.showInputDialog(this,
        "アップロードしたいファイルのURLを指定してください。",
        "http://ftp.jaist.ac.jp/pub/mergedoc/pleiades/4.5/pleiades-e4.5-java_20150624.zip");

    if(url==null)
    {
      return;
    }

    String file_name = "新しいファイル.txt";

    int index_slash = url.lastIndexOf("/");

    if(0<=index_slash && index_slash+1<url.length())
    {
      file_name = url.substring(index_slash+1);
    }

    String name = JOptionPane.showInputDialog(this,
        "アップロード後のファイル名を指定してください。",
        file_name);

    if(name==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    JProgressBar progress = new JProgressBar();
    progress.setStringPainted(true);
    JLabel label = new JLabel(url);
    JDialog dialog = new JDialog();
    dialog.setTitle("指定したURLから OneDrive へファイルをアップロード中... ");
    dialog.add(label, BorderLayout.NORTH);
    dialog.add(progress, BorderLayout.CENTER);
    dialog.setSize(new Dimension(600, 100));
    dialog.setVisible(true);

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<OneDriveItem, OneDriveProgressMonitor> worker = new SwingWorker<OneDriveItem, OneDriveProgressMonitor>()
    {
      @Override
      protected OneDriveItem doInBackground()
      {
        try
        {
          String url_monitor = client.uploadFileFromUrl(folder_current, url, name); // 進捗状況をモニターするためのURL。

          if(url_monitor==null)
          {
            return null;
          }

          while(true)
          {
            Thread.sleep(1000); // 1秒おきにサーバーに進捗状況を問い合わせる。

            Object object = client.monitorProgress(url_monitor);

            if(object==null) // エラーが発生した。
            {
              return null;
            }
            else if(object instanceof OneDriveItem) // URLからのアップロードが完了した。
            {
              return (OneDriveItem)object;
            }
            else if(object instanceof OneDriveProgressMonitor) // URLからのアップロードが進行中。
            {
              publish((OneDriveProgressMonitor)object);
            }
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          OneDriveItem item = get(); // doInBackground()の戻り値を受け取ります。

          if(item!=null) // アップロードが完了した場合。
          {
            progress.setValue(100);
            dialog.setTitle("指定したURLからのアップロードが完了しました。");
            getFolder(folder_current);
          }
          else
          {
            dialog.setTitle("指定したURLからのアップロードに失敗しました。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }

      /**
       * このメソッドは doInBackground()内の publish()メソッドに引き渡された情報を受け取ります。
       * @param chunks
       */
      @Override
      protected void process(List<OneDriveProgressMonitor> chunks)
      {
        for(OneDriveProgressMonitor c : chunks)
        {
          int percent = (int)c.percentageComplete;
          progress.setValue(percent);
        }
      }
    };

    worker.execute();
  }

  /**
   * 第16章：アイテムを指定したフォルダに移動する。
   */
  public void moveItem()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    OneDriveItem item = getSelectedItem();  // JTable上で選択されているアイテム。

    if(item==null)
    {
      return;
    }

    OneDriveItem item_folder = (OneDriveItem)JOptionPane.showInputDialog(this,
        "移動先フォルダを指定してください",
        "入力",
        JOptionPane.OK_CANCEL_OPTION,
        null,
        client.getCachedFolders(),
        null);

    if(item_folder==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("アイテム " + item.name + " をフォルダ " + item_folder.name + " に移動しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<OneDriveItem, Object> worker = new SwingWorker<OneDriveItem, Object>()
    {
      @Override
      protected OneDriveItem doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.moveItem(item, item_folder);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          OneDriveItem item_moved = get();

          if(item_moved!=null)
          {
            getFolder(folder_current); // 現在のフォルダ内のアイテム一覧をリフレッシュ表示。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "アイテムを移動しました。");
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "アイテムを移動出来ませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第16章：アイテムの名前を変更する。
   */
  public void renameItem()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    OneDriveItem item = getSelectedItem();  // JTable上で選択されているアイテム。

    if(item==null)
    {
      return;
    }

    String name = JOptionPane.showInputDialog(this,
        "アイテムの新しい名前を指定してください。",
        item.name);

    if(name==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("アイテム " + item.name + " の名前を " + name + " に変更しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<OneDriveItem, Object> worker = new SwingWorker<OneDriveItem, Object>()
    {
      @Override
      protected OneDriveItem doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.renameItem(item, name);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          OneDriveItem item_renamed = get();

          if(item_renamed!=null)
          {
            getFolder(folder_current); // 現在のフォルダ内のアイテム一覧をリフレッシュ表示。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "アイテムの名前を変更しました。");
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "アイテムの名前を変更出来ませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第16章：アイテムの説明をセットする。
   */
  public void setDescription()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    OneDriveItem item = getSelectedItem();  // JTable上で選択されているアイテム。

    if(item==null)
    {
      return;
    }

    String description = JOptionPane.showInputDialog(this,
        "アイテムの説明を入力てください。",
        (item.description!=null ? item.description : ""));

    if(description==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("アイテム " + item.name + " の説明を 「" + description + "」に変更しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<OneDriveItem, Object> worker = new SwingWorker<OneDriveItem, Object>()
    {
      @Override
      protected OneDriveItem doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.setDescription(item, description);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          OneDriveItem item_modified = get();

          if(item_modified!=null)
          {
            showMetaDataPanel(item_modified); // 説明を更新したアイテムのメタデータを表示。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "アイテムの説明を変更しました。");
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "アイテムの説明を変更出来ませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第17章：アイテムをコピーする。
   */
  private void copyItem()
  {
    OneDriveItem item = getSelectedItem();  // JTable上で選択されているアイテム。

    if(item==null)
    {
      return;
    }

    OneDriveItem item_folder = (OneDriveItem)JOptionPane.showInputDialog(this,
        "コピー先フォルダを指定してください",
        "入力",
        JOptionPane.OK_CANCEL_OPTION,
        null,
        client.getCachedFolders(),
        null);

    if(item_folder==null)
    {
      return;
    }

    String name = JOptionPane.showInputDialog(this,
          "コピー後の名前を指定してください。",
          item.name);

    if(name==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    JProgressBar progress = new JProgressBar();
    progress.setStringPainted(true);
    JLabel label = new JLabel(item_folder.getPath() + "/" + name);
    JDialog dialog = new JDialog();
    dialog.setTitle("アイテムをコピー中... ");
    dialog.add(label, BorderLayout.NORTH);
    dialog.add(progress, BorderLayout.CENTER);
    dialog.setSize(new Dimension(600, 100));
    dialog.setVisible(true);

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<OneDriveItem, OneDriveProgressMonitor> worker = new SwingWorker<OneDriveItem, OneDriveProgressMonitor>()
    {
      @Override
      protected OneDriveItem doInBackground()
      {
        try
        {
          String url_monitor = client.copyItem(item, item_folder, name); // 進捗状況をモニターするためのURL。

          if(url_monitor==null)
          {
            return null;
          }

          while(true)
          {
            Thread.sleep(1000); // 1秒おきにサーバーに進捗状況を問い合わせる。

            Object object = client.monitorProgress(url_monitor);

            if(object==null) // エラーが発生した。
            {
              return null;
            }
            else if(object instanceof OneDriveItem) // URLからのアップロードが完了した。
            {
              return (OneDriveItem)object;
            }
            else if(object instanceof OneDriveProgressMonitor) // URLからのアップロードが進行中。
            {
              publish((OneDriveProgressMonitor)object);
            }
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          OneDriveItem item = get(); // doInBackground()の戻り値を受け取ります。

          if(item!=null) // コピーが完了した場合。
          {
            progress.setValue(100);
            dialog.setTitle("アイテムのコピーが完了しました。");
            getFolder(folder_current);
          }
          else
          {
            dialog.setTitle("アイテムのコピーが出来ませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }

      /**
       * このメソッドは doInBackground()内の publish()メソッドに引き渡された情報を受け取ります。
       */
      @Override
      protected void process(List<OneDriveProgressMonitor> chunks)
      {
        for(OneDriveProgressMonitor c : chunks)
        {
          int percent = (int)c.percentageComplete;
          progress.setValue(percent);
        }
      }
    };

    worker.execute();
  }

  /**
   * 第17章：アイテムを削除する。
   */
  public void deleteItem()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    OneDriveItem item = getSelectedItem();  // JTable上で選択されているアイテム。

    if(item==null)
    {
      return;
    }

    int option = JOptionPane.showConfirmDialog(JavaOneDrive.this,
        "アイテム " + item.name + "を削除してもよろしいですか?",
        "確認",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.QUESTION_MESSAGE);

    if(option!=JOptionPane.YES_OPTION)
    {
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("アイテム " + item + " を削除しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<Boolean, Object> worker = new SwingWorker<Boolean, Object>()
    {
      @Override
      protected Boolean doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.deleteItem(item);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return false;
      }

      @Override
      protected void done()
      {
        try
        {
          boolean flag_success = get();

          if(flag_success==true)
          {
            getFolder(folder_current); // 現在のフォルダ内のアイテム一覧をリフレッシュ表示。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "アイテムを削除しました。");
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "アイテムを削除できませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第18章：サムネイル画像のセット。
   */
  private void setThumbnailImage()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    OneDriveItem item = getSelectedItem();  // JTable上で選択されているアイテム。

    if(item==null)
    {
      return;
    }
    else if(item.isFile()==false)
    {
      JOptionPane.showMessageDialog(this,
          "サムネイル画像はファイルにのみセットできます。");
      return;
    }
    else if(item.video!=null || item.photo!=null)
    {
      JOptionPane.showMessageDialog(this,
          "画像や動画のサムネイル画像は OneDriveサーバーが自動的に生成します。");
      return;
    }

    File file = showFileChooserToOpen(); // ファイルチューザーを表示してローカルのファイルを選択してもらう。

    if(file==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("OneDriveサーバーへサムネイル画像 " + file.getName() + " をアップロードしています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<Boolean, Object> worker = new SwingWorker<Boolean, Object>()
    {
      @Override
      protected Boolean doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.setThumbnailImage(item, file);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return false;
      }

      @Override
      protected void done()
      {
        try
        {
          boolean flag_success = get();

          if(flag_success==true)
          {
            getFolder(client.getCachedItem(item.parentReference.id)); // フォルダの内容を再度取得して表示する。
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this,
                "ファイル" + file.getName() + " をアップロードできませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第19章：OneDriveサーバー上のアイテムの共有リンクを取得する。
   */
  public void createSharingLink()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }


    OneDriveItem item = getSelectedItem();  // JTable上で選択されているアイテム。

    if(item==null)
    {
      JOptionPane.showMessageDialog(this, "共有するアイテムを選択してください。");
      return;
    }

    String[] options = {"view", "edit"};

    String type = (String)JOptionPane.showInputDialog(this,
        "共有方法を指定してください",
        "入力",
        JOptionPane.OK_CANCEL_OPTION,
        null,
        options,
        null);

    if(type==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("アイテム " + item.name + " の共有リンクを取得しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<String, Object> worker = new SwingWorker<String, Object>()
    {
      @Override
      protected String doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.createSharingLink(item, type);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。

          String link = get();

          if(link!=null)
          {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(link);
            clipboard.setContents(selection, selection);
            JOptionPane.showMessageDialog(JavaOneDrive.this,
                "共有リンクをクリップボードにコピーしました。\n"
                + link);
          }
          else
          {
            JOptionPane.showMessageDialog(JavaOneDrive.this,
                "共有リンクを取得出来ませんでした。");
          }
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第20章：キーワードを指定してアイテムを検索する。
   */
  public void searchItems()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    String keywords = JOptionPane.showInputDialog(this,
        "検索キーワードを指定してください。空白区切りで複数のキーワードを指定できます。",
        "キーワード");

    if(keywords==null)
    {
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("アイテムを検索しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<List<OneDriveItem>, Object> worker = new SwingWorker<List<OneDriveItem>, Object>()
    {
      @Override
      protected List<OneDriveItem> doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.searchItems(keywords);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          List<OneDriveItem> list = get();

          if(list!=null)
          {
            showSearchResultPanel(list); // 検索結果を表示。
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "検索結果を取得できませんでした。");
          }
        }
        catch(Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }

  /**
   * 第20章：検索結果のリストをGUI上に表示。
   */
  private void showSearchResultPanel(List<OneDriveItem> list_items)
  {
    panel_search_list.setItems(null, list_items);
    panel_search_thumbnails.setItems(null, list_items);
    showSearchResultPanel();
  }

  /**
   * 第20章：検索結果のリストをGUI上に表示。
   */
  private void showSearchResultPanel()
  {
    if(menu_view_list.isSelected()==true) // リスト表示モードの場合。
    {
      showPanel(panel_search_list);
    }
    else // サムネイル表示モードの場合。
    {
      showPanel(panel_search_thumbnails);
    }
  }

  /**
   * 第20章：表示モードの切り替えイベントを処理。
   */
  private void changeViewMode()
  {
    if(isCurrentPanelFolder()==true) // フォルダ内のアイテム一覧が表示されている場合。
    {
      showFolderPanel();
    }
    else if(isCurrentPanelSearchResult()==true) // 検索結果のアイテム一覧が表示されている場合。
    {
      showSearchResultPanel();
    }
  }

  /**
   * 第20章：現在GUI上に表示されているパネルがフォルダ内のアイテム一覧かどうかを返す。
   */
  private boolean isCurrentPanelFolder()
  {
    return (panel_current==panel_folder_list || panel_current==panel_folder_thumbnails);
  }

  /**
   * 第20章：現在GUI上に表示されているパネルが検索結果のアイテム一覧かどうかを返す。
   */
  private boolean isCurrentPanelSearchResult()
  {
    return (panel_current==panel_search_list || panel_current==panel_search_thumbnails);
  }

  /**
   * 第21章：最近の変更を取得。
   */
  public void getChanges()
  {
    if(isBusy()==true)
    {
      JOptionPane.showMessageDialog(this, "現在、他の処理を行っています。");
      return;
    }

    // 通信中のメッセージを表示。

    showStatusPanel("最近の変更を取得しています...");

    // バックグラウンドでサーバーにアクセスして、ネットワーク処理が終わったら GUIを更新する。

    SwingWorker<List<OneDriveItem>, Object> worker = new SwingWorker<List<OneDriveItem>, Object>()
    {
      @Override
      protected List<OneDriveItem> doInBackground() throws Exception
      {
        try
        {
          setBusy(true); // 通信処理を開始した。
          return client.getChanges();
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
        finally
        {
          setBusy(false); // 通信処理を終了した。
        }

        return null;
      }

      @Override
      protected void done()
      {
        try
        {
          List<OneDriveItem> list = get();

          if(list!=null)
          {
            showSearchResultPanel(list); // 検索結果を表示。
          }
          else
          {
            showFolderPanel(); // 以前に表示されていたリストをそのまま表示しなおす。
            JOptionPane.showMessageDialog(JavaOneDrive.this, "最近の変更を取得できませんでした。");
          }
        }
        catch(Exception e)
        {
          e.printStackTrace();
        }
      }
    };

    worker.execute();
  }
}