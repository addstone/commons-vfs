/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 */
package org.apache.commons.vfs.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avalon.excalibur.i18n.ResourceManager;
import org.apache.avalon.excalibur.i18n.Resources;
import org.apache.commons.AbstractVfsTestCase;
import org.apache.commons.vfs.FileContent;
import org.apache.commons.vfs.FileName;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileType;
import org.apache.commons.vfs.NameScope;
import org.apache.commons.vfs.impl.DefaultFileSystemManager;
import org.apache.commons.vfs.impl.VFSClassLoader;
import org.apache.commons.vfs.impl.PrivilegedFileReplicator;
import org.apache.commons.vfs.impl.DefaultFileReplicator;
import org.apache.commons.vfs.provider.AbstractFileObject;
import org.apache.commons.vfs.provider.local.DefaultLocalFileSystemProvider;

/**
 * File system test cases, which verifies the structure and naming
 * functionality.
 *
 * Works from a base folder, and assumes a particular structure under
 * that base folder.
 *
 * @author <a href="mailto:adammurdoch@apache.org">Adam Murdoch</a>
 * @version $Revision: 1.9 $ $Date: 2002/07/05 03:21:55 $
 */
public abstract class AbstractFileSystemTestCase
    extends AbstractVfsTestCase
{
    private static final Resources REZ =
        ResourceManager.getPackageResources( AbstractFileObject.class );

    protected FileObject m_baseFolder;
    protected DefaultFileSystemManager m_manager;

    // Contents of "file1.txt"
    protected String m_charContent;

    public AbstractFileSystemTestCase( String name )
    {
        super( name );
    }

    /**
     * Returns the name of the package containing a class.
     *
     * @return The . delimited package name, or an empty string if the class
     *         is in the default package.
     */
    protected static String getPackageName( final Class clazz )
    {
        final Package pkg = clazz.getPackage();
        if( null != pkg )
        {
            return pkg.getName();
        }

        final String name = clazz.getName();
        if( -1 == name.lastIndexOf( "." ) )
        {
            return "";
        }
        else
        {
            return name.substring( 0, name.lastIndexOf( "." ) );
        }
    }

    /**
     * Builds the expected folder structure.
     */
    private FileInfo buildExpectedStructure()
    {
        // Build the expected structure
        final FileInfo base = new FileInfo( "test", FileType.FOLDER );
        base.addChild( "file1.txt", FileType.FILE );
        base.addChild( "empty.txt", FileType.FILE );
        base.addChild( "emptydir", FileType.FOLDER );

        final FileInfo dir = new FileInfo( "dir1", FileType.FOLDER );
        base.addChild( dir );
        dir.addChild( "file1.txt", FileType.FILE );
        dir.addChild( "file2.txt", FileType.FILE );
        dir.addChild( "file3.txt", FileType.FILE );
        
        final FileInfo code = new FileInfo( "code", FileType.FOLDER );
        base.addChild( code );
        code.addChild( "ClassToLoad.class", FileType.FILE );
        return base;
    }

    /**
     * Returns the base folder to run the tests against.
     */
    protected abstract FileObject getBaseFolder() throws Exception;

    /**
     * Sets up the test
     */
    protected void setUp() throws Exception
    {
        // Create the file system manager
        m_manager = new DefaultFileSystemManager();
        m_manager.addProvider( "file", new DefaultLocalFileSystemProvider() );
        m_manager.setReplicator( new PrivilegedFileReplicator( new DefaultFileReplicator() ) );

        // Locate the base folder
        m_baseFolder = getBaseFolder();

        // Make some assumptions absout the name
        assertTrue( !m_baseFolder.getName().getPath().equals( "/" ) );

        // Build the expected content of "file1.txt"
        final String eol = "\n";
        m_charContent = "This is a test file." + eol + "With 2 lines in it." + eol;
    }

    /**
     * Cleans-up test.
     */
    protected void tearDown() throws Exception
    {
        m_manager.close();
    }

    /**
     * Tests resolution of absolute URI.
     */
    public void testAbsoluteURI() throws Exception
    {
        // Try fetching base folder again by its URI
        final String uri = m_baseFolder.getName().getURI();
        final FileObject file = m_manager.resolveFile( uri );

        assertSame( "file object", m_baseFolder, file );
    }

    /**
     * Tests resolution of relative file names via the FS manager
     */
    public void testRelativeURI() throws Exception
    {
        // Build base dir
        m_manager.setBaseFile( m_baseFolder );

        // Locate the base dir
        FileObject file = m_manager.resolveFile( "." );
        assertSame( "file object", m_baseFolder, file );

        // Locate a child
        file = m_manager.resolveFile( "some-child" );
        assertSame( "file object", m_baseFolder, file.getParent() );

        // Locate a descendent
        file = m_manager.resolveFile( "some-folder/some-file" );
        assertSame( "file object", m_baseFolder, file.getParent().getParent() );

        // Locate parent
        file = m_manager.resolveFile( ".." );
        assertSame( "file object", m_baseFolder.getParent(), file );
    }

    /**
     * Tests encoding of relative URI.
     */
    public void testRelativeUriEncoding() throws Exception
    {
        // Build base dir
        m_manager.setBaseFile( m_baseFolder );
        final String path = m_baseFolder.getName().getPath();

        // Encode "some file"
        FileObject file = m_manager.resolveFile( "%73%6f%6d%65%20%66%69%6c%65" );
        assertEquals( path + "/some file", file.getName().getPath() );

        // Encode "."
        file = m_manager.resolveFile( "%2e" );
        assertEquals( path, file.getName().getPath() );

        // Encode '%'
        file = m_manager.resolveFile( "a%25" );
        assertEquals( path + "/a%", file.getName().getPath() );

        // Encode /
        file = m_manager.resolveFile( "dir%2fchild" );
        assertEquals( path + "/dir/child", file.getName().getPath() );

        // Encode \
        file = m_manager.resolveFile( "dir%5cchild" );
        assertEquals( path + "/dir/child", file.getName().getPath() );

        // Use "%" literal
        try
        {
            m_manager.resolveFile( "%" );
            fail();
        }
        catch( FileSystemException e )
        {
        }

        // Not enough digits in encoded char
        try
        {
            m_manager.resolveFile( "%5" );
            fail();
        }
        catch( FileSystemException e )
        {
        }

        // Invalid digit in encoded char
        try
        {
            m_manager.resolveFile( "%q" );
            fail();
        }
        catch( FileSystemException e )
        {
        }
    }

    /**
     * Tests the root file name.
     */
    public void testRootFileName() throws Exception
    {
        // Locate the root file
        final FileName rootName = m_baseFolder.getRoot().getName();

        // Test that the root path is "/"
        assertEquals( "root path", "/", rootName.getPath() );

        // Test that the root basname is ""
        assertEquals( "root base name", "", rootName.getBaseName() );

        // Test that the root name has no parent
        assertNull( "root parent", rootName.getParent() );
    }

    /**
     * Tests child file names.
     */
    public void testChildName() throws Exception
    {
        final FileName baseName = m_baseFolder.getName();
        final String basePath = baseName.getPath();
        final FileName name = baseName.resolveName( "some-child", NameScope.CHILD );

        // Test path is absolute
        assertTrue( "is absolute", basePath.startsWith( "/" ) );

        // Test base name
        assertEquals( "base name", "some-child", name.getBaseName() );

        // Test absolute path
        assertEquals( "absolute path", basePath + "/some-child", name.getPath() );

        // Test parent path
        assertEquals( "parent absolute path", basePath, name.getParent().getPath() );

        // Try using a compound name to find a child
        assertBadName( name, "a/b", NameScope.CHILD );

        // Check other invalid names
        checkDescendentNames( name, NameScope.CHILD );
    }

    /**
     * Name resolution tests that are common for CHILD or DESCENDENT scope.
     */
    private void checkDescendentNames( final FileName name,
                                       final NameScope scope )
        throws Exception
    {
        // Make some assumptions about the name
        assertTrue( !name.getPath().equals( "/" ) );
        assertTrue( !name.getPath().endsWith( "/a" ) );
        assertTrue( !name.getPath().endsWith( "/a/b" ) );

        // Test names with the same prefix
        String path = name.getPath() + "/a";
        assertSameName( path, name, path, scope );
        assertSameName( path, name, "../" + name.getBaseName() + "/a", scope );

        // Test an empty name
        assertBadName( name, "", scope );

        // Test . name
        assertBadName( name, ".", scope );
        assertBadName( name, "./", scope );

        // Test ancestor names
        assertBadName( name, "..", scope );
        assertBadName( name, "../a", scope );
        assertBadName( name, "../" + name.getBaseName() + "a", scope );
        assertBadName( name, "a/..", scope );

        // Test absolute names
        assertBadName( name, "/", scope );
        assertBadName( name, "/a", scope );
        assertBadName( name, "/a/b", scope );
        assertBadName( name, name.getPath(), scope );
        assertBadName( name, name.getPath() + "a", scope );
    }

    /**
     * Checks that a relative name resolves to the expected absolute path.
     * Tests both forward and back slashes.
     */
    private void assertSameName( final String expectedPath,
                                 final FileName baseName,
                                 final String relName,
                                 final NameScope scope )
        throws Exception
    {
        // Try the supplied name
        FileName name = baseName.resolveName( relName, scope );
        assertEquals( expectedPath, name.getPath() );

        // Replace the separators
        relName.replace( '\\', '/' );
        name = baseName.resolveName( relName, scope );
        assertEquals( expectedPath, name.getPath() );

        // And again
        relName.replace( '/', '\\' );
        name = baseName.resolveName( relName, scope );
        assertEquals( expectedPath, name.getPath() );
    }

    /**
     * Checks that a relative name resolves to the expected absolute path.
     * Tests both forward and back slashes.
     */
    private void assertSameName( String expectedPath,
                                 FileName baseName,
                                 String relName ) throws Exception
    {
        assertSameName( expectedPath, baseName, relName, NameScope.FILE_SYSTEM );
    }

    /**
     * Tests relative name resolution, relative to the base folder.
     */
    public void testNameResolution() throws Exception
    {
        final FileName baseName = m_baseFolder.getName();
        final String parentPath = baseName.getParent().getPath();
        final String path = baseName.getPath();
        final String childPath = path + "/some-child";

        // Test empty relative path
        assertSameName( path, baseName, "" );

        // Test . relative path
        assertSameName( path, baseName, "." );

        // Test ./ relative path
        assertSameName( path, baseName, "./" );

        // Test .// relative path
        assertSameName( path, baseName, ".//" );

        // Test .///.///. relative path
        assertSameName( path, baseName, ".///.///." );
        assertSameName( path, baseName, "./\\/.\\//." );

        // Test <elem>/.. relative path
        assertSameName( path, baseName, "a/.." );

        // Test .. relative path
        assertSameName( parentPath, baseName, ".." );

        // Test ../ relative path
        assertSameName( parentPath, baseName, "../" );

        // Test ..//./ relative path
        assertSameName( parentPath, baseName, "..//./" );
        assertSameName( parentPath, baseName, "..//.\\" );

        // Test <elem>/../.. relative path
        assertSameName( parentPath, baseName, "a/../.." );

        // Test <elem> relative path
        assertSameName( childPath, baseName, "some-child" );

        // Test ./<elem> relative path
        assertSameName( childPath, baseName, "./some-child" );

        // Test ./<elem>/ relative path
        assertSameName( childPath, baseName, "./some-child/" );

        // Test <elem>/././././ relative path
        assertSameName( childPath, baseName, "./some-child/././././" );

        // Test <elem>/../<elem> relative path
        assertSameName( childPath, baseName, "a/../some-child" );

        // Test <elem>/<elem>/../../<elem> relative path
        assertSameName( childPath, baseName, "a/b/../../some-child" );
    }

    /**
     * Tests descendent name resolution.
     */
    public void testDescendentName()
        throws Exception
    {
        final FileName baseName = m_baseFolder.getName();

        // Test direct child
        String path = baseName.getPath() + "/some-child";
        assertSameName( path, baseName, "some-child", NameScope.DESCENDENT );

        // Test compound name
        path = path + "/grand-child";
        assertSameName( path, baseName, "some-child/grand-child", NameScope.DESCENDENT );

        // Test relative names
        assertSameName( path, baseName, "./some-child/grand-child", NameScope.DESCENDENT );
        assertSameName( path, baseName, "./nada/../some-child/grand-child", NameScope.DESCENDENT );
        assertSameName( path, baseName, "some-child/./grand-child", NameScope.DESCENDENT );

        // Test badly formed descendent names
        checkDescendentNames( baseName, NameScope.DESCENDENT );
    }

    /**
     * Tests resolution of absolute names.
     */
    public void testAbsoluteNames() throws Exception
    {
        // Test against the base folder
        FileName name = m_baseFolder.getName();
        checkAbsoluteNames( name );

        // Test against the root
        name = m_baseFolder.getRoot().getName();
        checkAbsoluteNames( name );

        // Test against some unknown file
        name = name.resolveName( "a/b/unknown" );
        checkAbsoluteNames( name );
    }

    /**
     * Tests resolution of absolute names.
     */
    private void checkAbsoluteNames( final FileName name ) throws Exception
    {
        // Root
        assertSameName( "/", name, "/" );
        assertSameName( "/", name, "//" );
        assertSameName( "/", name, "/." );
        assertSameName( "/", name, "/some file/.." );

        // Some absolute names
        assertSameName( "/a", name, "/a" );
        assertSameName( "/a", name, "/./a" );
        assertSameName( "/a", name, "/a/." );
        assertSameName( "/a/b", name, "/a/b" );

        // Some bad names
        assertBadName( name, "/..", NameScope.FILE_SYSTEM );
        assertBadName( name, "/a/../..", NameScope.FILE_SYSTEM );
    }

    /**
     * Asserts that a particular relative name is invalid for a particular
     * scope.
     */
    private void assertBadName( final FileName name,
                                final String relName,
                                final NameScope scope )
    {
        try
        {
            name.resolveName( relName, scope );
            fail( "expected failure" );
        }
        catch( FileSystemException e )
        {
            // TODO - should check error message
        }
    }

    /**
     * Tests conversion from absolute to relative names.
     */
    public void testAbsoluteNameConvert() throws Exception
    {
        final FileName baseName = m_baseFolder.getName();

        String path = "/test1/test2";
        FileName name = baseName.resolveName( path );
        assertEquals( path, name.getPath() );

        // Try child and descendent names
        testRelName( name, "child" );
        testRelName( name, "child1/child2" );

        // Try own name
        testRelName( name, "." );

        // Try parent, and root
        testRelName( name, ".." );
        testRelName( name, "../.." );

        // Try sibling and descendent of sibling
        testRelName( name, "../sibling" );
        testRelName( name, "../sibling/child" );

        // Try siblings with similar names
        testRelName( name, "../test2_not" );
        testRelName( name, "../test2_not/child" );
        testRelName( name, "../test" );
        testRelName( name, "../test/child" );

        // Try unrelated
        testRelName( name, "../../unrelated" );
        testRelName( name, "../../test" );
        testRelName( name, "../../test/child" );

        // Test against root
        path = "/";
        name = baseName.resolveName( path );
        assertEquals( path, name.getPath() );

        // Try child and descendent names (against root)
        testRelName( name, "child" );
        testRelName( name, "child1/child2" );

        // Try own name (against root)
        testRelName( name, "." );
    }

    /**
     * Checks that a file name converts to an expected relative path
     */
    private void testRelName( final FileName baseName,
                              final String relPath )
        throws Exception
    {
        final FileName expectedName = baseName.resolveName( relPath );

        // Convert to relative path, and check
        final String actualRelPath = baseName.getRelativeName( expectedName );
        assertEquals( relPath, actualRelPath );
    }

    /**
     * Walks the base folder structure, asserting it contains exactly the
     * expected files and folders.
     */
    public void testStructure() throws Exception
    {
        final FileInfo baseInfo = buildExpectedStructure();
        assertSameStructure( m_baseFolder, baseInfo );
    }

    /**
     * Walks a folder structure, asserting it contains exactly the
     * expected files and folders.
     */
    protected void assertSameStructure( final FileObject folder,
                                        final FileInfo expected )
        throws Exception
    {
        // Setup the structure
        final List queueExpected = new ArrayList();
        queueExpected.add( expected );

        final List queueActual = new ArrayList();
        queueActual.add( folder );

        while( queueActual.size() > 0 )
        {
            final FileObject file = (FileObject)queueActual.remove( 0 );
            final FileInfo info = (FileInfo)queueExpected.remove( 0 );

            // Check the type is correct
            assertSame( file.getType(), info.m_type );

            if( info.m_type == FileType.FILE )
            {
                continue;
            }

            // Check children
            final FileObject[] children = file.getChildren();

            // Make sure all children were found
            assertNotNull( children );
            assertEquals( "count children of \"" + file.getName() + "\"", info.m_children.size(), children.length );

            // Recursively check each child
            for( int i = 0; i < children.length; i++ )
            {
                final FileObject child = children[ i ];
                final FileInfo childInfo = (FileInfo)info.m_children.get( child.getName().getBaseName() );

                // Make sure the child is expected
                assertNotNull( childInfo );

                // Add to the queue of files to check
                queueExpected.add( childInfo );
                queueActual.add( child );
            }
        }
    }

    /**
     * Tests existence determination.
     */
    public void testExists() throws Exception
    {
        // Test a file
        FileObject file = m_baseFolder.resolveFile( "file1.txt" );
        assertTrue( "file exists", file.exists() );

        // Test a folder
        file = m_baseFolder.resolveFile( "dir1" );
        assertTrue( "folder exists", file.exists() );

        // Test an unknown file
        file = m_baseFolder.resolveFile( "unknown-child" );
        assertTrue( "unknown file does not exist", !file.exists() );

        // Test an unknown file in an unknown folder
        file = m_baseFolder.resolveFile( "unknown-folder/unknown-child" );
        assertTrue( "unknown file does not exist", !file.exists() );
    }

    /**
     * Tests type determination.
     */
    public void testType() throws Exception
    {
        // Test a file
        FileObject file = m_baseFolder.resolveFile( "file1.txt" );
        assertSame( FileType.FILE, file.getType() );

        // Test a folder
        file = m_baseFolder.resolveFile( "dir1" );
        assertSame( FileType.FOLDER, file.getType() );

        // Test an unknown file
        file = m_baseFolder.resolveFile( "unknown-child" );
        try
        {
            file.getType();
            fail();
        }
        catch( FileSystemException e )
        {
            final String message = REZ.getString( "get-type-no-exist.error", file );
            assertSameMessage( message, e );
        }
    }

    /**
     * Tests parent identity
     */
    public void testParent() throws FileSystemException
    {
        // Test when both exist
        FileObject folder = m_baseFolder.resolveFile( "dir1" );
        FileObject child = folder.resolveFile( "file3.txt" );
        assertTrue( "folder exists", folder.exists() );
        assertTrue( "child exists", child.exists() );
        assertSame( folder, child.getParent() );

        // Test when file does not exist
        child = folder.resolveFile( "unknown-file" );
        assertTrue( "folder exists", folder.exists() );
        assertTrue( "child does not exist", !child.exists() );
        assertSame( folder, child.getParent() );

        // Test when neither exists
        folder = m_baseFolder.resolveFile( "unknown-folder" );
        child = folder.resolveFile( "unknown-file" );
        assertTrue( "folder does not exist", !folder.exists() );
        assertTrue( "child does not exist", !child.exists() );
        assertSame( folder, child.getParent() );

        // Test root of the file system has no parent
        FileObject root = m_baseFolder.getRoot();
        assertNull( "root has null parent", root.getParent() );
    }

    /**
     * Tests that children cannot be listed for non-folders.
     */
    public void testChildren() throws FileSystemException
    {
        // Check for file
        FileObject file = m_baseFolder.resolveFile( "file1.txt" );
        assertSame( FileType.FILE, file.getType() );
        try
        {
            file.getChildren();
            fail();
        }
        catch( FileSystemException e )
        {
            final String message = REZ.getString( "list-children-not-folder.error", file );
            assertSameMessage( message, e );
        }

        // Should be able to get child by name
        file = file.resolveFile( "some-child" );
        assertNotNull( file );

        // Check for unknown file
        file = m_baseFolder.resolveFile( "unknown-file" );
        assertTrue( !file.exists() );
        try
        {
            file.getChildren();
            fail();
        }
        catch( FileSystemException e )
        {
            final String message = REZ.getString( "list-children-no-exist.error", file );
            assertSameMessage( message, e );
        }

        // Should be able to get child by name
        FileObject child = file.resolveFile( "some-child" );
        assertNotNull( child );
    }

    /**
     * Tests VFSClassLoader.
     */
    public void testVFSClassLoader() throws Exception
    {
        final FileObject[] objects = { m_baseFolder };
        VFSClassLoader loader =
            new VFSClassLoader( objects, m_manager );

        Class testClass = loader.loadClass( "code.ClassToLoad" );
        Object testObject = testClass.newInstance();
        assertSame( "**PRIVATE**", testObject.toString() );

        URL resource = loader.getResource( "file1.txt" );
        assertNotNull( resource );
        URLConnection urlCon = resource.openConnection();
        assertSameURLContent( m_charContent, urlCon );
    }

    /**
     * Tests url.
     */
    public void testURL() throws Exception
    {
        FileObject file = m_baseFolder.resolveFile( "some-dir/" );
        URL url = file.getURL();

        assertEquals( file.getName().getURI(), url.toExternalForm() );

        URL parentURL = new URL( url, ".." );
        assertEquals( m_baseFolder.getURL(), parentURL );

        URL rootURL = new URL( url, "/" );
        assertEquals( file.getRoot().getURL(), rootURL );
    }

    /**
     * Tests content.
     */
    public void testURLContent() throws Exception
    {
        // Test non-empty file
        FileObject file = m_baseFolder.resolveFile( "file1.txt" );
        URLConnection urlCon = file.getURL().openConnection();
        assertSameURLContent( m_charContent, urlCon );

        // Test empty file
        file = m_baseFolder.resolveFile( "empty.txt" );
        urlCon = file.getURL().openConnection();
        assertSameURLContent( "", urlCon );
    }

    /**
     * Asserts that the content of a file is the same as expected. Checks the
     * length reported by getContentLength() is correct, then reads the content
     * as a byte stream and compares the result with the expected content.
     * Assumes files are encoded using UTF-8.
     */
    protected void assertSameURLContent( final String expected,
                                         final URLConnection connection )
        throws Exception
    {
        // Get file content as a binary stream
        final byte[] expectedBin = expected.getBytes( "utf-8" );

        // Check lengths
        assertEquals( "same content length", expectedBin.length, connection.getContentLength() );

        // Read content into byte array
        final InputStream instr = connection.getInputStream();
        final ByteArrayOutputStream outstr;
        try
        {
            outstr = new ByteArrayOutputStream();
            final byte[] buffer = new byte[ 256 ];
            int nread = 0;
            while( nread >= 0 )
            {
                outstr.write( buffer, 0, nread );
                nread = instr.read( buffer );
            }
        }
        finally
        {
            instr.close();
        }

        // Compare
        assertTrue( "same binary content", Arrays.equals( expectedBin, outstr.toByteArray() ) );
    }

    /**
     * Tests that folders and unknown files have no content.
     */
    public void testNoURLContent() throws Exception
    {
        // Try getting the content of a folder
        FileObject folder = m_baseFolder.resolveFile( "dir1" );
        try
        {
            folder.getURL().openConnection().getInputStream();
            fail();
        }
        catch( IOException e )
        {
            final String message = REZ.getString( "read-folder.error", folder );
            assertSameMessage( message, e );
        }

        // Try getting the content of an unknown file
        FileObject unknownFile = m_baseFolder.resolveFile( "unknown-file" );
        URLConnection connection = unknownFile.getURL().openConnection();
        try
        {
            connection.getInputStream();
            fail();
        }
        catch( IOException e )
        {
            final String message = REZ.getString( "read-no-exist.error", unknownFile );
            assertSameMessage( message, e );
        }
        assertEquals( -1, connection.getContentLength() );
    }

    /**
     * Tests content.
     */
    public void testContent() throws Exception
    {
        // Test non-empty file
        FileObject file = m_baseFolder.resolveFile( "file1.txt" );
        FileContent content = file.getContent();
        assertSameContent( m_charContent, content );

        // Test empty file
        file = m_baseFolder.resolveFile( "empty.txt" );
        content = file.getContent();
        assertSameContent( "", content );
    }

    /**
     * Asserts that the content of a file is the same as expected. Checks the
     * length reported by getSize() is correct, then reads the content as
     * a byte stream and compares the result with the expected content.
     * Assumes files are encoded using UTF-8.
     */
    protected void assertSameContent( final String expected,
                                      final FileContent content )
        throws Exception
    {
        // Get file content as a binary stream
        final byte[] expectedBin = expected.getBytes( "utf-8" );

        // Check lengths
        assertEquals( "same content length", expectedBin.length, content.getSize() );

        // Read content into byte array
        final InputStream instr = content.getInputStream();
        final ByteArrayOutputStream outstr;
        try
        {
            outstr = new ByteArrayOutputStream();
            final byte[] buffer = new byte[ 256 ];
            int nread = 0;
            while( nread >= 0 )
            {
                outstr.write( buffer, 0, nread );
                nread = instr.read( buffer );
            }
        }
        finally
        {
            instr.close();
        }

        // Compare
        assertTrue( "same binary content", Arrays.equals( expectedBin, outstr.toByteArray() ) );
    }

    /**
     * Tests that folders and unknown files have no content.
     */
    public void testNoContent() throws Exception
    {
        // Try getting the content of a folder
        FileObject folder = m_baseFolder.resolveFile( "dir1" );
        try
        {
            folder.getContent().getInputStream();
            fail();
        }
        catch( FileSystemException e )
        {
            final String message = REZ.getString( "read-folder.error", folder );
            assertSameMessage( message, e );
        }

        // Try getting the content of an unknown file
        FileObject unknownFile = m_baseFolder.resolveFile( "unknown-file" );
        FileContent content = unknownFile.getContent();
        try
        {
            content.getInputStream();
            fail();
        }
        catch( FileSystemException e )
        {
            final String message = REZ.getString( "read-no-exist.error", unknownFile );
            assertSameMessage( message, e );
        }
        try
        {
            content.getSize();
            fail();
        }
        catch( FileSystemException e )
        {
            final String message = REZ.getString( "get-size-no-exist.error", unknownFile );
            assertSameMessage( message, e );
        }
    }

    /**
     * Tests that content and file objects are usable after being closed.
     */
    public void testReuse() throws Exception
    {
        // Get the test file
        FileObject file = m_baseFolder.resolveFile( "file1.txt" );
        assertEquals( FileType.FILE, file.getType() );

        // Get the file content
        FileContent content = file.getContent();
        assertSameContent( m_charContent, content );

        // Read the content again
        content = file.getContent();
        assertSameContent( m_charContent, content );

        // Close the content + file
        content.close();
        file.close();

        // Read the content again
        content = file.getContent();
        assertSameContent( m_charContent, content );
    }

    /**
     * Info about a file.
     */
    protected static final class FileInfo
    {
        String m_baseName;
        FileType m_type;
        Map m_children = new HashMap();

        public FileInfo( final String name, final FileType type )
        {
            m_baseName = name;
            m_type = type;
        }

        /** Adds a child. */
        public void addChild( final FileInfo child )
        {
            m_children.put( child.m_baseName, child );
        }

        /** Adds a child. */
        public void addChild( final String baseName, final FileType type )
        {
            addChild( new FileInfo( baseName, type ) );
        }

        /** Adds a bunch of children. */
        public void addChildren( final String[] baseNames, final FileType type )
        {
            for( int i = 0; i < baseNames.length; i++ )
            {
                String baseName = baseNames[ i ];
                addChild( new FileInfo( baseName, type ) );
            }
        }
    }
}
