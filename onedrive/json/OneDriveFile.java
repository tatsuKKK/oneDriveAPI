package onedrive.json;

import java.io.Serializable;

public class OneDriveFile implements Serializable
{
  private static final long serialVersionUID = 7599493683099291053L;

  public OneDriveHashes hashes;
  public String mimeType;
}
