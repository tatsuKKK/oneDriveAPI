package onedrive.json;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.google.gson.annotations.SerializedName;

public class OneDriveItem implements Serializable
{
  private static final long serialVersionUID = 6836575239623909074L;

  public String id;
  public String name;
  public String eTag;
  public String cTag;
  public OneDriveIdentitySet createdBy;
  public OneDriveIdentitySet lastModifiedBy;
  public String createdDateTime;
  public String lastModifiedDateTime;
  public long size;
  public OneDriveItemReference parentReference;
  public String webUrl;
  public String description;
  public OneDriveFolder folder;
  public OneDriveFile file;
  public OneDriveFileSystemInfo fileSystemInfo;
  public OneDriveImage image;
  public OneDrivePhoto photo;
  public OneDriveAudio audio;
  public OneDriveVideo video;
  public OneDriveLocation location;
  public OneDriveDeleted deleted;
  public List<OneDriveItem> children;

  @SerializedName("@content.downloadUrl") // このアノテーションを指定するには SerializedName をインポートする必要があります。
  public String content_downloadUrl;

  /**
   * このアイテムがファイルかどうかを返す。
   */
  public boolean isFile()
  {
    return (file!=null);
  }

  /**
   * このアイテムがフォルダーかどうかを返す。
   */
  public boolean isFolder()
  {
    return (folder!=null);
  }

  /**
   * このアイテムがルートフォルダーかどうかを返す。
   */
  public boolean isRootFolder()
  {
    if(isFolder()==true && parentReference==null)
    {
      return true;
    }

    return false;
  }

  /**
   * 第8章：このアイテムのIDを返す。
   */
  public String getID()
  {
    return id;
  }

  /**
   * 第8章：このアイテムの親のIDを返す。
   */
  public String getParentID()
  {
    return (parentReference!=null ? parentReference.id : null);
  }

  /**
   * 第8章：このアイテムのパスを返す。
   */
  public String getPath()
  {
    if(this.isRootFolder()==true) // ルートフォルダの場合。
    {
      return "/drive/root:";
    }
    else // 親フォルダがある場合。
    {
      String path_parent = getParentPath();

      if(path_parent!=null)
      {
        return path_parent + "/" + name;
      }
    }

    return null;
  }

  /**
   * 第8章：このアイテムの親のパスを返す。
   */
  public String getParentPath()
  {
    if(parentReference!=null && parentReference.path!=null)
    {
      try
      {
        String path = URLDecoder.decode(parentReference.path, "UTF-8");
        return path;
      }
      catch (UnsupportedEncodingException e)
      {
        e.printStackTrace();
      }
    }

    return null;
  }

  /**
   * 第9章：アイテムが最後に編集されたUTC時刻を返す。
   */
  public Date getLastModifiedDateTime()
  {
    if(lastModifiedDateTime==null)
    {
      return null;
    }

    try
    {
      // 文字列の例  → 2015-08-01T12:30:40.567Z
      SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      Date date = date_format.parse(lastModifiedDateTime);
      return date;
    }
    catch(Exception e)
    {
    }

    try
    {
      // 文字列の例  → 2015-08-01T12:30:40Z
      SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
      Date date = date_format.parse(lastModifiedDateTime);
      return date;
    }
    catch(Exception e)
    {
    }

    return null;
  }

  /**
   * 第9章：アイテムが最後に編集された時刻をテキストで返す。UTCとの時間差を指定してください。
   */
  public String getLastModifiedDateTimeText(String format, int utc_diff_hour)
  {
    Date date = getLastModifiedDateTime();

    if(date!=null)
    {
      date.setTime(date.getTime() + utc_diff_hour*60*60*1000); // UTCとの差を調整する。
      SimpleDateFormat date_format = new SimpleDateFormat(format);
      return date_format.format(date);
    }

    return null;
  }

  /**
   * 第16章：アイテムのパスを返す。
   */
  @Override
  public String toString()
  {
    return this.getPath();
  }
}