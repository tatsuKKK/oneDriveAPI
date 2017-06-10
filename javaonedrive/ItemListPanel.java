package javaonedrive;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import onedrive.json.OneDriveItem;

public class ItemListPanel extends JPanel
{
  private OneDriveItem item_folder = null;
  private List<OneDriveItem> list_items = new ArrayList<OneDriveItem>();
  private JTable table;
  private ItemListTableModel model = null;
  private JButton button = null;

  public ItemListPanel(JavaOneDrive frame)
  {
    this.model = new ItemListTableModel();
    this.table = new JTable(this.model);
    this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    this.table.addMouseListener(new java.awt.event.MouseAdapter()
    {
      public void mouseClicked(MouseEvent e)
      {
        if(e.getClickCount()==1 && SwingUtilities.isRightMouseButton(e)==true) // 第9章：右クリックされた場合。
        {
          java.awt.Point point = e.getPoint(); // 右クリックされたポイント。
          int index = table.rowAtPoint(point);  // 右クリックされた列。
          table.setRowSelectionInterval(index, index); // 右クリックされた列を選択状態にする。
          frame.showPopupMenu(table, (int)point.getX(), (int)point.getY()); // 右クリックされた位置にポップアップメニューを表示。
        }
        else if(e.getClickCount()==2)
        {
          int row = ItemListPanel.this.table.getSelectedRow();

          if(list_items!=null && 0<=row && row<list_items.size())
          {
            OneDriveItem item = list_items.get(row);

            if(item!=null)
            {
              if(item.isFolder()==true)
              {
                frame.getFolder(item); // このフォルダ内のアイテムリストを表示。
              }
              else if(item.isFile()==true)
              {
                // frame.downloadFile(item); // 第10章：ファイルのダウンロード。
                frame.startDownloader(item); // 第11章：ファイルのダウンローダーを起動。
              }
            }
          }
        }
      }
    });

    // 画面下部に配置するボタン。

    button = new JButton("戻る");
    button.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        if(item_folder!=null && item_folder.parentReference!=null)
        {
          OneDriveItem item_parent = frame.getOneDriveClient().getCachedItem(item_folder.getParentID());

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

    this.setLayout(new BorderLayout());
    this.add(new JScrollPane(this.table), BorderLayout.CENTER);
    this.add(button, BorderLayout.SOUTH);
  }

  /**
   * 表示したいフォルダとそのフォルダ内のアイテムのリストをセットする。
   */
  public void setItems(OneDriveItem item_folder, List<OneDriveItem> list_items)
  {
    this.item_folder = item_folder;
    this.list_items = list_items;
    this.model.fireTableDataChanged();
    this.button.setEnabled(this.item_folder==null || this.item_folder.isRootFolder()==false);
  }

  /**
   * フォルダとファイルのリストを表示するテーブルモデル。
   */
  private class ItemListTableModel extends DefaultTableModel
  {
    String[] column = {"名前", "種類", "サイズ", "更新日時"};

    @Override
    public int getRowCount()
    {
      return (list_items!=null ? list_items.size() : 0);
    }

    @Override
    public int getColumnCount()
    {
      return this.column.length;
    }

    @Override
    public String getColumnName(int column)
    {
      return this.column[column];
    }

    @Override
    public Object getValueAt(int row, int column)
    {
      try
      {
        if(list_items!=null)
        {
          OneDriveItem item = list_items.get(row);

          if(column==0) return item.name;
          if(column==1) return (item.isFile() ? "ファイル" : (item.isFolder() ? "フォルダ" : "削除済み"));
          if(column==2) return item.size;
          if(column==3) return item.getLastModifiedDateTimeText("yyyy/MM/dd HH:mm", 9); // 第9章：UTCと日本の時差は9時間。
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }

      return "";
    }

    @Override
    public boolean isCellEditable(int row, int column)
    {
      return false;
    }
  }

  /**
   * GUI上で選択されているアイテムを返す。
   */
  public OneDriveItem getSelectedItem()
  {
    if(this.list_items!=null)
    {
      int index = this.table.getSelectedRow();

      if(0<=index && index<this.list_items.size())
      {
        return this.list_items.get(index);
      }
    }

    return null;
  }
}