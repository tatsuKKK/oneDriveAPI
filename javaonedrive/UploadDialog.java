package javaonedrive;

import java.awt.Dimension;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import onedrive.OneDrive;
import onedrive.OneDriveUploader;

public class UploadDialog extends JDialog
{
  private static String TEXT_SHOW_META_DATA = "メタデータ";
  private static String TEXT_CANCEL         = "一時停止";
  private static String TEXT_RESTART        = "再開";

  private JavaOneDrive frame;
  private OneDrive client;
  private JPanel panel_uploads = new JPanel();
  private HashMap<OneDriveUploader, UploadPanel> map = new HashMap<OneDriveUploader, UploadPanel>();

  public UploadDialog(JavaOneDrive frame, OneDrive client)
  {
    super();
    this.frame = frame;
    this.client = client;
    setTitle("アップロード");
    setSize(new Dimension(800, 400));
    panel_uploads.setLayout(new BoxLayout(panel_uploads, BoxLayout.Y_AXIS));
    panel_uploads.setSize(new Dimension(800, 400));
    add(new JScrollPane(panel_uploads));
    this.setUploaders();
  }

  public void setUploaders()
  {
    int size = client.getUploaderSize();

    for(int i=0;i<size;i++)
    {
      OneDriveUploader uploader = client.getUploader(i);

      if(uploader!=null)
      {
        UploadPanel p = map.get(uploader);

        if(p==null)
        {
          p = new UploadPanel(uploader);
          map.put(uploader, p);
          panel_uploads.add(p);
        }
      }
    }
  }

  private class UploadPanel extends JPanel implements OneDriveUploader.UploadListener, ActionListener
  {
    OneDriveUploader uploader;
    Label label = new Label();
    JProgressBar progress = new JProgressBar();
    JButton button_control = new JButton("");
    JButton button_clear = new JButton("X");

    public UploadPanel(OneDriveUploader uploader)
    {
      this.uploader = uploader;
      uploader.setUploadListener(this);
      File file = uploader.getLocalFile();
      label.setText(file!=null ? file.getName() : "");
      label.setPreferredSize(new Dimension(200, 30));
      progress.setStringPainted(true);
      button_control.setPreferredSize(new Dimension(120, 30));
      button_control.addActionListener(this);
      button_clear.setPreferredSize(new Dimension(40, 30));
      button_clear.addActionListener(this);
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setMaximumSize(new Dimension(1200, 36));
      add(label);
      add(progress);
      add(button_control);
      add(button_clear);
      setPanel();
    }

    private void setPanel()
    {
      progress.setValue((int)uploader.getProgress());

      if(uploader.isCompleted()==true)
      {
        button_control.setText(TEXT_SHOW_META_DATA);
      }
      else if(uploader.isUploading()==true)
      {
        button_control.setText(TEXT_CANCEL);
      }
      else
      {
        button_control.setText(TEXT_RESTART);
      }

      button_control.setEnabled(true);
    }

    @Override
    public void progress(OneDriveUploader uploader)
    {
      // リスナーは別スレッドから呼ばれるので、処理をGUIスレッドにまわす。

      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          setPanel();
        }
      });
    }

    @Override
    public void actionPerformed(ActionEvent event)
    {
      Object source = event.getSource();

      if(source==button_control)
      {
        if(button_control.getText().compareTo(TEXT_SHOW_META_DATA)==0)
        {
          frame.showMetaDataPanel(uploader.getUploadedItem());
        }
        else if(button_control.getText().compareTo(TEXT_CANCEL)==0)
        {
          uploader.requestCancel();
        }
        else if(button_control.getText().compareTo(TEXT_RESTART)==0)
        {
          frame.startUploader(uploader);
        }
      }
      else if(source==button_clear)
      {
        button_clear.setEnabled(false); // 二重クリックを防止するため、無効にする。
        uploader.requestCancel();
        uploader.remove();
        panel_uploads.remove(this);
        panel_uploads.revalidate();
        panel_uploads.repaint();
      }
    }
  }
}
