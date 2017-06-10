package onedrive.json;

import java.io.Serializable;

public class OneDriveIdentitySet implements Serializable
{
  private static final long serialVersionUID = 2451895593398835794L;

  public OneDriveIdentity user;
  public OneDriveIdentity application;
  public OneDriveIdentity device;
}
