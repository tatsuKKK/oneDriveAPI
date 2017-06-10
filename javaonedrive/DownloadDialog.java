package javaonedrive;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import onedrive.OneDrive;
import onedrive.OneDriveDownloader;

public class DownloadDialog extends JDialog
{
  private static String TEXT_OPEN_FOLDER = "フォルダを開く";
  private static String TEXT_CANCEL      = "一時停止";
  private static String TEXT_RESTART     = "再開";

  private JavaOneDrive frame;
  private OneDrive client;
  private JPanel panel_downloads = new JPanel();
  private HashMap<OneDriveDownloader, DownloadPanel> map = new HashMap<OneDriveDownloader, DownloadPanel>();

  public DownloadDialog(JavaOneDrive frame, OneDrive client)
  {
    super();
    this.frame = frame;
    this.client = client;
    setTitle("ダウンロード");
    setSize(new Dimension(800, 400));
    panel_downloads.setLayout(new BoxLayout(panel_downloads, BoxLayout.Y_AXIS));
    panel_downloads.setSize(new Dimension(800, 400));
    add(new JScrollPane(panel_downloads));
    this.setDownloaders();
  }

  public void setDownloaders()
  {
    int size = client.getDownloaderSize();

    for(int i=0;i<size;i++)
    {
      OneDriveDownloader downloader = client.getDownloader(i);

      if(downloader!=null)
      {
        DownloadPanel p = map.get(downloader);

        if(p==null)
        {
          p = new DownloadPanel(downloader);
          map.put(downloader, p);
          panel_downloads.add(p);
        }
      }
    }
  }

  private class DownloadPanel extends JPanel implements OneDriveDownloader.DownloadListener, ActionListener
  {
    OneDriveDownloader downloader;
    Label label = new Label();
    JProgressBar progress = new JProgressBar();
    JButton button_control = new JButton("");
    JButton button_clear = new JButton("X");

    public DownloadPanel(OneDriveDownloader downloader)
    {
      this.downloader = downloader;
      downloader.setDownloadListener(this);
      File file = downloader.getLocalFile();
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
      progress.setValue((int)downloader.getProgress());

      if(downloader.isCompleted()==true)
      {
        button_control.setText(TEXT_OPEN_FOLDER);
        button_control.setEnabled(downloader.getLocalFile().exists());
      }
      else if(downloader.isDownloading()==true)
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
    public void progress(OneDriveDownloader downloader)
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
        if(button_control.getText().compareTo(TEXT_OPEN_FOLDER)==0)
        {
          openFolder(downloader.getLocalFile());
        }
        else if(button_control.getText().compareTo(TEXT_CANCEL)==0)
        {
          downloader.requestCancel();
        }
        else if(button_control.getText().compareTo(TEXT_RESTART)==0)
        {
          frame.startDownloader(downloader);
        }
      }
      else if(source==button_clear)
      {
        button_clear.setEnabled(false); // 二重クリックを防止するため、無効にする。
        downloader.requestCancel();
        downloader.remove();
        panel_downloads.remove(this);
        panel_downloads.revalidate();
        panel_downloads.repaint();
      }
    }
  }

  private void openFolder(File file)
  {
    try
    {
      Desktop.getDesktop().open(file.getParentFile());
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
  }
}