package javaonedrive;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import onedrive.OneDrive;
import onedrive.json.OneDriveItem;

public class ItemThumbnailPanel extends JPanel
{
  static int THUMBNAIL_BUTTON_SIZE = 180;

  private JavaOneDrive frame = null;
  private OneDriveItem item_folder = null;
  private List<OneDriveItem> list_items = null;
  private ArrayList<ThumbnailButton> list_thumbnails = null;
  private JPanel panel_thumbnails = null;
  private JButton button = null;

  public ItemThumbnailPanel(JavaOneDrive frame)
  {
    this.frame = frame;
  }

  /**
   * アイテムのサムネイル画像をダウンロードして描画する。
   */
  public void setItems(OneDriveItem item_folder, List<OneDriveItem> list_items)
  {
    this.item_folder = item_folder;
    this.list_items = list_items;
    this.panel_thumbnails = new JPanel();
    this.panel_thumbnails.setLayout(new FlowLayout(FlowLayout.LEFT));
    this.panel_thumbnails.setPreferredSize(new Dimension(820, 600));

    MouseListener listener = new java.awt.event.MouseAdapter()
    {
      public void mouseClicked(MouseEvent e)
      {
        Object source = e.getSource();

        if(source instanceof ThumbnailButton)
        {
          ThumbnailButton button = (ThumbnailButton)source;

          if(e.getClickCount()==1 && SwingUtilities.isRightMouseButton(e)==true)
          {
            button.setSelected(true); // 右クリックされたボタンを選択状態にする。
            java.awt.Point point = e.getPoint(); // 右クリックされたポイント。
            frame.showPopupMenu(button, (int)point.getX(), (int)point.getY()); // 右クリックされた位置にポップアップメニューを表示。
          }
          else if(e.getClickCount()==2)
          {
            OneDriveItem item = button.item;

            if(item!=null)
            {
              if(item.isFolder()==true)
              {
                frame.getFolder(item); // このフォルダ内のアイテムリストを表示。
              }
              else if(item.isFile()==true)
              {
                frame.startDownloader(item); // このファイルをダウンロード。frame.downloadFile(item)
              }
            }
          }
        }
      }
    };

    this.list_thumbnails = new ArrayList<ThumbnailButton>();

    ButtonGroup group = new ButtonGroup();

    for(OneDriveItem item : list_items)
    {
      String text = item.name + " (" + (item.isFile() ? "ファイル" : (item.isFolder() ? "フォルダ" : "削除済み")) + ")";
      JLabel label = new JLabel(text, JLabel.CENTER);
      ThumbnailButton thumbnail = new ThumbnailButton(item);
      group.add(thumbnail); // ボタンの排他処理。
      this.list_thumbnails.add(thumbnail); // サムネイル画像のリスト。
      thumbnail.addMouseListener(listener);
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(thumbnail, "Center");
      panel.add(label, "South");
      this.panel_thumbnails.add(panel);
    }

    // 別スレッドでサムネイル画像を順にダウンロードする。

    this.downloadThumbnails(this.list_thumbnails);

    // 画面下部に配置するボタン。

    this.button = new JButton("戻る");
    this.button.setEnabled(item_folder==null || item_folder.isRootFolder()==false);
    this.button.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        if(item_folder!=null && item_folder.parentReference!=null)
        {
          OneDriveItem item_parent = frame.getOneDriveClient().getCachedItem(item_folder.parentReference.id);

          if(item_parent!=null) // 親フォルダの情報が見つかった場合。
          {
            frame.getFolder(item_parent); // 親フォルダ内のアイテム一覧を表示。
          }
        }
        else
        {
          frame.showFolderPanel();
        }
      }
    });

    this.removeAll();
    this.setLayout(new BorderLayout());
    this.add(new JScrollPane(this.panel_thumbnails), "Center");
    this.add(this.button, "South");
    this.revalidate();
    this.repaint();
  }

  /**
   * サムネイル画像を表示するボタン。
   */
  public class ThumbnailButton extends JToggleButton
  {
    OneDriveItem item;
    Image image;

    public ThumbnailButton(OneDriveItem item)
    {
      this.item = item;
      this.setPreferredSize(new Dimension(THUMBNAIL_BUTTON_SIZE, THUMBNAIL_BUTTON_SIZE)); // ボタンのサイズを画像より少し大きめにする。
    }

    public void setImage(Image image)
    {
      this.image = image;

      this.setIcon(new Icon()
      {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
          AlphaComposite composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f);
          ((Graphics2D)g).setComposite(composite);

          int w = image.getWidth(null);
          int h = image.getHeight(null);

          if(w>0 && h>0)
          {
            int marging = 8;

            if(w<h) // 縦長の場合は上下を切り取って表示。
            {
              g.drawImage(image,
                  marging, marging, THUMBNAIL_BUTTON_SIZE-marging, THUMBNAIL_BUTTON_SIZE-marging,
                  0, (h-w)/2, w, h-(h-w)/2, ThumbnailButton.this);
            }
            else // 横長の場合は左右を切り取って表示。
            {
              g.drawImage(image,
                  marging, marging, THUMBNAIL_BUTTON_SIZE-marging, THUMBNAIL_BUTTON_SIZE-marging,
                  (w-h)/2, 0, w-(w-h)/2, h, ThumbnailButton.this);
            }
          }
        }

        @Override
        public int getIconWidth()
        {
          return THUMBNAIL_BUTTON_SIZE;
        }

        @Override
        public int getIconHeight()
        {
          return THUMBNAIL_BUTTON_SIZE;
        }
      });
    }
  }

  /**
   * GUI上で選択されているアイテムを返す。
   */
  public OneDriveItem getSelectedItem()
  {
    for(ThumbnailButton button : this.list_thumbnails)
    {
      if(button.isSelected()==true)
      {
        return button.item;
      }
    }

    return null;
  }

  /**
   * 別スレッドでサムネイル画像をダウンロードする。
   */
  private void downloadThumbnails(final ArrayList<ThumbnailButton> list)
  {
    Thread thread = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        OneDrive client = frame.getOneDriveClient();

        for(ThumbnailButton button : list) // ボタンに表示するサムネイル画像を順に更新する。
        {
          byte[] image_bytes = client.getChachedThumbnailImage(button.item);

          if(image_bytes!=null) // キャッシュしてある画像がある場合。
          {
            setImage(button, image_bytes);
          }
        }

        for(ThumbnailButton button : list) // ボタンに表示するサムネイル画像を順に更新する。
        {
          byte[] image_bytes = client.getChachedThumbnailImage(button.item);

          if(image_bytes==null) // キャッシュしてある画像がない場合。
          {
            image_bytes = client.getThumbnailImage(button.item); // サムネイル画像のダウンロード。

            if(image_bytes!=null)
            {
              setImage(button, image_bytes);
            }
          }
        }
      }
    });

    thread.start();
  }

  /**
   * サムネイル画像のバイト列を受け取ってボタン上に描画する。
   */
  private void setImage(ThumbnailButton button, byte[] image_bytes)
  {
    if(image_bytes!=null)
    {
      try
      {
        // バイト列を BufferedImage に変換。

        final BufferedImage image = ImageIO.read(new ByteArrayInputStream(image_bytes));

        // GUIスレッドで描画。

        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            button.setImage(image);
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
