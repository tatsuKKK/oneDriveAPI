package onedrive.json;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class OneDriveItemList
{
  public List<OneDriveItem> value;

  @SerializedName("@odata.nextLink")
  public String odata_nextLink;

  @SerializedName("@changes.hasMoreChanges")
  public boolean changes_hasMoreChanges;

  @SerializedName("@odata.deltaLink")
  public String odata_deltaLink;

  @SerializedName("@delta.token")
  public String delta_token;
}
