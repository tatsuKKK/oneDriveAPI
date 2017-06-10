package onedrive.json;

import java.io.Serializable;

/**
 * 第6章：アクセストークンとリフレッシュトークンを保管しておくための器。
 * Gsonに対応するため、変数名は変更しないでください。
 */
public class OneDriveToken implements Serializable
{
  private static final long serialVersionUID = -2245653596898884570L;

  public String token_type;
  public int expires_in;
  public String scope;
  public String access_token;
  public String refresh_token;
  public String user_id;
  private long time_received; // この変数だけは JSONのパラメータに含まれていない。

  /**
   * アクセストークンを取得した時刻をセットする。
   */
  public void setTime(long time_received)
  {
    this.time_received = time_received;
  }

  /**
   * 第7章：アクセストークンが有効期限内かどうかを調べる。
   */
  public boolean isAvailable()
  {
    try
    {
      if(this.access_token!=null)
      {
        long time_current = System.currentTimeMillis();
        long time_limit   = time_received + expires_in*1000;
        return (time_current<time_limit);
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }

    return false;
  }
}
