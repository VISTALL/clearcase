package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.List;

public class CheckOutAction extends SynchronousAction
{
  @NonNls private static final String ACTION_NAME = "Check Out";
  @NonNls private static final String CHECKOUT_HIJACKED_TITLE = "Check out hijacked file";
  @NonNls private static final String NOT_A_VOB_OBJECT_SIG = "Not a vob object";
  @NonNls private static final String IS_ALREADY_CHECKED_OUT_SIG = "is already checked out";

  protected String getActionName( AnActionEvent e )
  {
    boolean verbose = getHost( e ).getCheckoutOptions().getValue();
    return verbose ? ACTION_NAME + "..." : ACTION_NAME;
  }

  public void update( AnActionEvent e )
  {
    super.update( e );

    TransparentVcs host = getHost( e );
    boolean isVisible = (host != null && host.getConfig() != null);
    e.getPresentation().setVisible( isVisible );
    e.getPresentation().setEnabled( isVisible && !host.getConfig().isOffline );  
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    if( !VcsUtil.isPathUnderProject( _actionProjectInstance, file ))
      return false;

    //  NB: if invoked for a folder, the status is most often "NOT_CHANGED"
    FileStatus status = getFileStatus( _actionProjectInstance, file );
    return status == FileStatus.NOT_CHANGED || status == FileStatus.HIJACKED;
  }

  protected void execute( AnActionEvent e, List<VcsException> errors )
  {
    String comment = "";
    VirtualFile[] files = VcsUtil.getVirtualFiles( e );

    if( _hostInstance.getCheckoutOptions().getValue() )
    {
      CheckoutDialog dialog = ( files.length == 1 ) ?
                                new CheckoutDialog( _actionProjectInstance, files[ 0 ] ) :
                                new CheckoutDialog( _actionProjectInstance, files );
      dialog.show();
      if( dialog.getExitCode() == CheckoutDialog.CANCEL_EXIT_CODE )
        return;

      comment = dialog.getComment();
    }

    for( VirtualFile file : files )
    {
      performOnFile( e, file, comment, errors );
    }
  }

  private void performOnFile( AnActionEvent e, VirtualFile file,
                              String comment, List<VcsException> errors )
  {
    if( isEnabled( file, e ) )
    {
      VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance( _actionProjectInstance );
      try
      {
        perform( file, comment );
        mgr.fileDirty( file );
      }
      catch( VcsException ex ) {
        if( !isIgnorableMessage( ex.getMessage() ) )
        {
          ex.setVirtualFile( file );
          errors.add( ex );
        }
      }
      catch ( RuntimeException ex ) {
        if( !isIgnorableMessage( ex.getMessage() ) )
        {
          VcsException vcsEx = new VcsException( ex );
          vcsEx.setVirtualFile( file );
          errors.add( vcsEx );
        }
      }
      executeRecursively( e, file, comment, errors );
    }
  }

  private void executeRecursively( AnActionEvent e, VirtualFile file, String comment, List<VcsException> errors )
  {
    if( file.isDirectory() )
    {
      for( VirtualFile child : file.getChildren() )
        performOnFile( e, child, comment, errors );
    }
  }

  protected void perform( VirtualFile file, String comment ) throws VcsException
  {
    //  Checkout command can be issued for a folder - we do not support this as
    //  the separate operation.
    if( file.isDirectory() )
      return;

    FileStatus status = getFileStatus( _actionProjectInstance, file );
    if( status == FileStatus.UNKNOWN || status == FileStatus.MODIFIED )
      return;

    boolean keepHijack = false;
    if( status == FileStatus.HIJACKED )
    {
      @NonNls String message = "The file " + file.getPresentableUrl() + " has been hijacked. \n" +
                               "Would you like to use it as the checked-out file?\nIf not it will be lost.";
      int answer = Messages.showYesNoDialog( message, CHECKOUT_HIJACKED_TITLE, Messages.getQuestionIcon() );
      keepHijack = (answer == 0);
    }

    try
    {
      _hostInstance.checkoutFile( file, keepHijack, comment );

      //  Assign the special marker to the file indicating that there is no need
      //  to run <cleartool> command on the file - it is known to be modified
      //  after the checkout command.
      file.putUserData( TransparentVcs.SUCCESSFUL_CHECKOUT, true );
      file.refresh( true, file.isDirectory() );
    }
    catch( ClearCaseException exc )
    {
      VcsException vcsExc = new VcsException( exc );
      AbstractVcsHelper.getInstance( _actionProjectInstance ).showError( vcsExc, ACTION_NAME );
    }
  }

  private static boolean isIgnorableMessage( String message )
  {
    return message.indexOf( NOT_A_VOB_OBJECT_SIG ) != -1 ||
           message.indexOf( IS_ALREADY_CHECKED_OUT_SIG ) != -1;
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    //  We should never reach this point. Most methods are overloaded to support
    //  adding uniform data (here - comment) to the operation.
    throw new NotImplementedException();
  }
}
