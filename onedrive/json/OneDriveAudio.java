package onedrive.json;

import java.io.Serializable;

public class OneDriveAudio implements Serializable
{
  private static final long serialVersionUID = -8078287926522467932L;

  public String album;
  public String albumArtist;
  public String artist;
  public int bitrate;
  public String composers;
  public String copyright;
  public int disc;
  public int discCount;
  public long duration;
  public String genre;
  public boolean hasDrm;
  public boolean isVariableBitrate;
  public String title;
  public int track;
  public int trackCount;
  public int year;
}
