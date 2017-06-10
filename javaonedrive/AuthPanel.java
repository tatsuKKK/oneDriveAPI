package javaonedrive;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import onedrive.OneDrive;

public class AuthPanel extends JPanel
{
  private static int MODE_SIGN_IN  = 0;
  private static int MODE_SIGN_OUT = 1;

  private JavaOneDrive frame;
  private JFXPanel panel_fx = new JFXPanel();
  private WebEngine web_engine;
  private int mode = MODE_SIGN_IN;

  public AuthPanel(JavaOneDrive frame)
  {
    this.frame = frame;
    add(panel_fx);
  }

  /**
   * サインインのWEB画面を表示。
   */
  public void signIn()
  {
    this.initWebView(MODE_SIGN_IN);
  }

  /**
   * サインアウトのWEB画面を表示。
   */
  public void signOut()
  {
    this.initWebView(MODE_SIGN_OUT);
  }

  /**
   * JavaFXスレッド上でWEB画面を表示する処理を行う。
   */
  public void initWebView(final int mode)
  {
    // モードをセット。MODE_SIGN_IN か MODE_SIGN_OUT のどちらか。

    this.mode = mode;

    // JavaFX用のスレッドでコンポーネントの初期化を行う。

    Platform.runLater(new Runnable()
    {
      @Override
      public void run()
      {
        WebView web_view = new WebView();
        web_engine = web_view.getEngine();
        Worker<Void> worker = web_engine.getLoadWorker();
        worker.stateProperty().addListener(new AuthChangeListener()); // WEB画面の変化の通知を受け取るリスナー。
        String url = null;

        if(mode==MODE_SIGN_IN)
        {
          url = frame.getOneDriveClient().getSignInUrl(OneDrive.LANGUAGE_JAPANESE); // マイクロソフトのサインインページのURL。
        }
        else
        {
          url = frame.getOneDriveClient().getSignOutUrl(); //  マイクロソフトのサインアウトページのURL。
          frame.clearOneDriveClient(); // クライアント情報をクリアする。
        }

        web_engine.load(url); // マイクロソフトの認証用WEBページをロード。
        Scene scene = new Scene(web_view); // WebView を Scene に格納。
        panel_fx.setScene(scene); // Scene を JFXPanel に格納。
      }
    });
  }

  /**
   * WebView上に表示されている画面の遷移をモニタリングするためのリスナー。
   */
  public class AuthChangeListener implements ChangeListener<Worker.State>
  {
    @Override
    public void changed(ObservableValue value, Worker.State state_old, Worker.State state_new)
    {
      try
      {
        if(state_new!=Worker.State.SUCCEEDED)
        {
          return; // WEB画面の表示がまだ成功裏に完了していない。
        }

        String url = web_engine.getLocation(); // 現在表示されているWEB画面のURL。

        if(url.indexOf(OneDrive.URL_AUTHORIZE + OneDrive.SRF_REDIRECT)!=0)
        {
          return; // リダイレクトされた「真っ白なページ」ではない。
        }

        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            if(mode==MODE_SIGN_IN)
            {
              OneDrive client = frame.getOneDriveClient();
              String code = client.getCodeFromUrl(url); // URLの中に含まれている認証コードを抜き出す。

              if(code!=null)
              {
                frame.requestAccessTokenByCode(code); // 第6章：認証コードを元にアクセストークンを取得する。
              }
              else
              {
                JOptionPane.showMessageDialog(frame,
                    "認証コードを取得できませんでした。");
              }
            }
            else // サインアウト
            {
              JOptionPane.showMessageDialog(frame,
                  "サインアウトしました。");
            }
          }
        });
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
    }
  }
}