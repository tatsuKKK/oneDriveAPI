package onedrive.json;

import java.io.Serializable;

public class OneDrivePhoto implements Serializable
{
  private static final long serialVersionUID = -4825276237952422646L;

  public String takenDateTime;
  public String cameraMake;
  public String cameraModel;
  public float fNumber;
  public int exposureDenominator;
  public int exposureNumerator;
  public float focalLength;
  public int iso;
}
