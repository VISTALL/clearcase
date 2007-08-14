package net.sourceforge.transparent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.ChangeManagement.CCaseContentRevision;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: May 14, 2007
 */
public class ContentRevisionFactory
{
  private static VFSKeysListener listener;
  private static HashMap<FilePath, CCaseContentRevision> cachedRevisions;

  static
  {
    cachedRevisions = new HashMap<FilePath, CCaseContentRevision>();
    listener = new VFSKeysListener();
    LocalFileSystem.getInstance().addVirtualFileListener( listener );
  }

  public static void detachListeners()
  {
    LocalFileSystem.getInstance().removeVirtualFileListener( listener );
  }

  private ContentRevisionFactory() {}

  public static CCaseContentRevision getRevision( @NotNull FilePath path, Project project )
  {
    CCaseContentRevision revision = cachedRevisions.get( path );
    if( revision == null )
    {
      revision = new CCaseContentRevision( path, project );
      cachedRevisions.put( path, revision );
    }
    return revision;
  }

  public static void clearCacheForFile( String file )
  {
    FilePath path = VcsUtil.getFilePath( file );
    cachedRevisions.remove( path );
  }

  private static class VFSKeysListener extends VirtualFileAdapter
  {
    public VFSKeysListener() {}

    public void beforeFileMovement( VirtualFileMoveEvent e )
    {
      String oldPath = e.getOldParent().getPath() + "/" + e.getFileName();
      analyzeEvent( oldPath );
    }

    public void beforePropertyChange( VirtualFilePropertyEvent e )
    {
      final VirtualFile file = e.getFile();
      if( e.getPropertyName() == VirtualFile.PROP_NAME )
      {
        try
        {
          String oldName = file.getParent().getPath() + "/" + e.getOldValue();
          analyzeEvent( oldName );
        }
        catch( NullPointerException exc )
        {
          //  Nothing to do - file is not suitable (no parent)
        }
      }
    }

    private static void analyzeEvent( String filePath )
    {
      FilePath path = VcsUtil.getFilePath( filePath );
      CCaseContentRevision revision = cachedRevisions.get( path );
      if( revision != null )
      {
        cachedRevisions.remove( path );
      }
    }
  }
}
