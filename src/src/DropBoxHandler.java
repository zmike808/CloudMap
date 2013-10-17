/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package src;

import com.dropbox.core.DbxAccountInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxEntry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map.*;
import java.util.*;
/**
 *
 * @author Michael
 */

public class DropBoxHandler {
    DbxClient client;
    ArrayList<DbxEntry> nLevels;
    HashMap<String,Long> folderHash;
    ArrayList<String> allFolderNames;
    Long fSize;
   
    private void loadHash(File file) throws IOException, ClassNotFoundException
    { 
        if(!file.exists())
        {
            return;
        }
        FileInputStream f = new FileInputStream(file);
        ObjectInputStream s = new ObjectInputStream(f);
        folderHash = (HashMap<String, Long>) s.readObject();
        s.close();
    }
 
    private void saveHash(File file) throws IOException
    {
        if(file.exists())
        {
            file.delete();
            file.createNewFile();
        }
        FileOutputStream f = new FileOutputStream(file);
        ObjectOutputStream s = new ObjectOutputStream(f);
        s.writeObject(folderHash);
        s.close();
    }
    
   
    
    private void createHash() throws DbxException, IOException, ClassNotFoundException
    {
        File file = new File("hashCache");
        if(file.exists())
        {
            loadHash(file);
            return;
        }
        folderHash = new HashMap<String,Long>();
        allFolderNames = new ArrayList<String>();
        allFolderNames.add("/");
       
        getAllFolders("/");
        Collections.sort(allFolderNames, new customComparator());
        //allFolderNames.
        for(String path : allFolderNames) //optimized for efficeny
        {
            getFolderSize(path);
            System.out.println(path);
            folderHash.put(path,fSize);
        }
        
        saveHash(file); //cache that hash
    }
    //ideally would be used for when updating the hashfile is needed
    private void createHash(boolean update) throws DbxException, IOException, ClassNotFoundException
    {
        folderHash = new HashMap<String,Long>();
        File file = new File("hashCache");
        if(file.exists())
        {
            loadHash(file);
            if(!update)
            {
                return;
            }
        }
        
        
        
        allFolderNames = new ArrayList<String>();
        allFolderNames.add("/");
       
        /*
        fill array list with all folder names (prefixed by their full path from the root directory)
        i.e /photos, /photos/1/6/2
        */
        getAllFolders("/");
        
        /*
         * sorts folders by number of "/" in path name
         * essentially sorts the folders by depth, with the deepest possible directories 
         * being the first in the arraylist allFolderNames
          i.e /photos/public/shared/1/trip2/etc, /photos/public/shared/1/trip2, etc...
         */
        Collections.sort(allFolderNames, new customComparator()); 
        
        for(String path : allFolderNames) //optimized for efficeny
        {
            if(folderHash.containsKey(path))
            {
            } 
            else {
                getFolderSize(path);
                System.out.println(path); //for debug purposes and to let user know it hasn't frozen...
                folderHash.put(path,fSize);
            }
        }
        
        saveHash(file); //save that hash to disk
    }
    
    public long getQuota() throws DbxException
    {
        DbxAccountInfo accinfo = client.getAccountInfo();
       
        return accinfo.quota.normal;
    }
    public long getFreeSpace() throws DbxException
    {
        DbxAccountInfo accinfo = client.getAccountInfo();
        
        return accinfo.quota.total - getQuota();
    }
    public ArrayList<DbxEntry> getFilesInDir(String dir, int n) throws DbxException
    {
        nLevels = new ArrayList<DbxEntry>();
        getFilesInDirHelper(dir,n);
        return nLevels;
        
    }
    private void getFilesInDirHelper(String dir, int n) throws DbxException
    {
        if(n!=0){
            DbxEntry.WithChildren root = client.getMetadataWithChildren(dir);
            if(root.children.isEmpty())
            {
                return; //no childs you got to the end
            }
            for(DbxEntry ent : root.children)
            {
                if(ent.isFile()){
                   nLevels.add(ent);
                }
                else if(ent.isFolder())
                {
                    nLevels.add(ent);
                    getFilesInDirHelper(ent.path, n-1);
                }
            }
         }    
    }
    
    public DropBoxHandler(String auth_token, String projName) throws DbxException, IOException, ClassNotFoundException
    {
        DbxRequestConfig req_conf = new DbxRequestConfig(projName, Locale.getDefault().toString()); 
        client = new DbxClient(req_conf, auth_token);
        nLevels = new ArrayList<DbxEntry>();
        createHash(true);
    }
    
  
    private void getAllFolders(String dir) throws DbxException, IOException
    {
        try
        {
        DbxEntry.WithChildren root = client.getMetadataWithChildren(dir);
        for(DbxEntry ent : root.children)
        {
            if(ent.isFolder() && !(folderHash.containsKey(ent.path)))
            {
                System.out.println("Folder added: " + ent.path);
                allFolderNames.add(ent.path);
                getAllFolders(ent.path);
            }
        }
        }
        catch(DbxException | IOException e)
        {
            File f = new File("hashCache");
            saveHash(f); //save hash before we crash so we can start where we left off
            System.out.println("Error occured, saving current progress");
        }
    }
    
    public Long getFolderSize(String dir) throws DbxException
    {
        //Long l = new Long();
        fSize = new Long(0);
   
        getSizeOfFolderHelper(dir);
        //System.out.print("Folder: " + dir + " -- ");
       // System.out.println(fSize);
       return fSize;
    }
    private void getSizeOfFolderHelper(String dir) throws DbxException
    {
       DbxEntry.WithChildren root = client.getMetadataWithChildren(dir);
       
       if(root.children.isEmpty())
       {
           return;
        }
       for(DbxEntry ent : root.children)
       {
           if(folderHash.containsKey(ent.path))
           {
               fSize += folderHash.get(ent.path); //basically saves time by using previously summed lower directories
           }
          
          //should never need an else to handle adding new folders, since only files provide a size
           else if(ent.isFile())
           {
               fSize += ent.asFile().numBytes;
           }
       }
    }
}
     

