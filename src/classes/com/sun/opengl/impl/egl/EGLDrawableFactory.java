/*
 * Copyright (c) 2008 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * Sun gratefully acknowledges that this software was originally authored
 * and developed by Kenneth Bradley Russell and Christopher John Kline.
 */

package com.sun.opengl.impl.egl;

import java.util.*;
import javax.media.opengl.*;
import com.sun.opengl.impl.*;
import com.sun.gluegen.runtime.NativeLibrary;

public class EGLDrawableFactory extends GLDrawableFactoryImpl {
  
    // We need more than one of these on certain devices (the NVidia APX 2500 in particular)
    private List/*<NativeLibrary>*/ glesLibraries;

    public EGLDrawableFactory() {
        super();

        loadGLESLibrary();
        EGL.resetProcAddressTable(this);
    }

    private void loadGLESLibrary() {
        List/*<NativeLibrary>*/ libs = new ArrayList();

        // Try several variants
        List/*<String>*/ glesLibNames = new ArrayList();

        if (GLProfile.isGLES2()) {
            // Unix
            glesLibNames.add("libGLESv2");
            glesLibNames.add("libGLESv2_CM");
            // Windows
            glesLibNames.add("GLESv2");
            glesLibNames.add("GLESv2_CM");
        } else if (GLProfile.isGLES1()) {
            // Unix
            glesLibNames.add("libGLES_CM");
            glesLibNames.add("libGLES_CL");
            glesLibNames.add("libGLESv1_CM");

            // Windows
            glesLibNames.add("GLES_CM");
            glesLibNames.add("GLES_CL");
            glesLibNames.add("GLESv1_CM");
        } else {
            throw new GLException("Invalid GL Profile for EGL: "+GLProfile.getProfile());
        }

        // EGL Unix
        glesLibNames.add("libEGL");
        // EGL Windows
        glesLibNames.add("EGL");

        ClassLoader loader = getClass().getClassLoader();
        for (Iterator iter = glesLibNames.iterator(); iter.hasNext(); ) {
            NativeLibrary lib = NativeLibrary.open((String) iter.next(), loader);
            if (lib != null) {
                libs.add(lib);
                break;
            }
        }

        if (libs.isEmpty()) {
            throw new GLException("Unable to dynamically load OpenGL ES library for profile \"" + GLProfile.getProfile() + "\"");
        }

        glesLibraries = libs;

        if (GLProfile.isGLES2()) {
            NativeLibLoader.loadES2();
        } else if (GLProfile.isGLES1()) {
            NativeLibLoader.loadES1();
        }
    }

    public AbstractGraphicsConfiguration chooseGraphicsConfiguration(GLCapabilities capabilities,
                                                                     GLCapabilitiesChooser chooser,
                                                                     AbstractGraphicsDevice device) {
        return null;
    }

    public GLDrawable createGLDrawable(NativeWindow target,
                                    GLCapabilities capabilities,
                                    GLCapabilitiesChooser chooser) {
        target = NativeWindowFactory.getNativeWindow(target);
        return new EGLDrawable(this, target,
                               capabilities,
                               chooser);
    }

    public GLDrawableImpl createOffscreenDrawable(GLCapabilities capabilities,
                                                  GLCapabilitiesChooser chooser) {
        throw new GLUnsupportedException("Not yet implemented");
    }

    public boolean canCreateGLPbuffer() {
        // Not supported on OpenGL ES
        return false;
    }
    public GLPbuffer createGLPbuffer(final GLCapabilities capabilities,
                                     final GLCapabilitiesChooser chooser,
                                     final int initialWidth,
                                     final int initialHeight,
                                     final GLContext shareWith) {
        throw new GLUnsupportedException("Pbuffer support not available on OpenGL ES");
    }

    public GLContext createExternalGLContext() {
        return new EGLExternalContext();
    }

    public boolean canCreateExternalGLDrawable() {
        return false;
    }

    public GLDrawable createExternalGLDrawable() {
        throw new GLUnsupportedException("Not yet implemented");
    }

    public void loadGLULibrary() {
    }

    public long dynamicLookupFunction(String glFuncName) {
        // Look up this function name in all known libraries
        // 
        // Note that we aren't using eglGetProcAddress; maybe we
        // should once it's bootstrapped (FIXME)
        for (Iterator iter = glesLibraries.iterator(); iter.hasNext(); ) {
            NativeLibrary lib = (NativeLibrary) iter.next();
            long addr = lib.lookupFunction(glFuncName);
            if (addr != 0) {
                return addr;
            }
        }

        return 0;
    }

    public boolean canCreateContextOnJava2DSurface() {
        return false;
    }

    public int[] glCapabilities2AttribList(GLCapabilities caps) {
        int[] attrs = new int[] {
                EGL.EGL_RENDERABLE_TYPE, EGL.EGL_OPENGL_ES_BIT,
                // FIXME: does this need to be configurable?
                EGL.EGL_SURFACE_TYPE,    EGL.EGL_WINDOW_BIT,
                EGL.EGL_RED_SIZE,        caps.getRedBits(),
                EGL.EGL_GREEN_SIZE,      caps.getGreenBits(),
                EGL.EGL_BLUE_SIZE,       caps.getBlueBits(),
                EGL.EGL_ALPHA_SIZE,      (caps.getAlphaBits() > 0 ? caps.getAlphaBits() : EGL.EGL_DONT_CARE),
                EGL.EGL_STENCIL_SIZE,    (caps.getStencilBits() > 0 ? caps.getStencilBits() : EGL.EGL_DONT_CARE),
                EGL.EGL_DEPTH_SIZE,      caps.getDepthBits(),
                EGL.EGL_NONE
            };
        if (GLProfile.isGLES2()) {
            // ES 2
            attrs[1] = EGL.EGL_OPENGL_ES2_BIT;
        }

        return attrs;
    }

    /*

    // FIXME: this is the OpenGL ES 2 initialization order

    // Initialize everything
    public void initialize() throws GLException {
        System.out.println("EGLDrawableFactory.initEGL()");
        if (!initEGL()) {
            throw new GLException("EGL init failed");
        }
        System.out.println("EGLDrawableFactory.chooseConfig()");
        if (!chooseConfig()) {
            throw new GLException("EGL choose config failed");
        }
        System.out.println("EGLDrawableFactory.checkDisplay()");
        if (!checkDisplay()) {
            throw new GLException("EGL check display failed");
        }
        System.out.println("EGLDrawableFactory.checkConfig()");
        if (!checkConfig()) {
            throw new GLException("EGL check config failed");
        }
        System.out.println("EGLDrawableFactory.createWindow()");
        if (!createWindow()) {
            throw new GLException("KD window init failed");
        }
        System.out.println("EGLDrawableFactory.setWindowVisible()");
        setWindowVisible();
        System.out.println("EGLDrawableFactory.setWindowFullscreen()");
        setWindowFullscreen();
        System.out.println("EGLDrawableFactory.realizeWindow()");
        if (!realizeWindow()) {
            throw new GLException("EGL/GLES window realize failed");
        }
        System.out.println("EGLDrawableFactory.createSurface()");
        if (!createSurface()) {
            throw new GLException("EGL create window surface failed");
        }
        System.out.println("EGLDrawableFactory.createContext()");
        if (!createContext()) {
            throw new GLException("EGL create context failed");
        }
        System.out.println("EGLDrawableFactory.makeCurrent()");
        if (!makeCurrent()) {
            throw new GLException("EGL make current failed");
        }
        System.out.println("EGLDrawableFactory.updateWindowSize()");
        updateWindowSize();
    }

    */

    /*

    // FIXME: this is the OpenGL ES 1 initialization order

    // Initialize everything
    public void initialize() throws GLException {
        System.out.println("EGLDrawableFactory.initEGL()");
        if (!initEGL()) {
            throw new GLException("EGL init failed");
        }
        System.out.println("EGLDrawableFactory.chooseConfig()");
        if (!chooseConfig()) {
            throw new GLException("EGL choose config failed");
        }
        System.out.println("EGLDrawableFactory.checkDisplay()");
        if (!checkDisplay()) {
            throw new GLException("EGL check display failed");
        }
        System.out.println("EGLDrawableFactory.checkConfig()");
        if (!checkConfig()) {
            throw new GLException("EGL check config failed");
        }
        System.out.println("EGLDrawableFactory.createContext()");
        if (!createContext()) {
            throw new GLException("EGL create context failed");
        }
        //
        // OpenKODE Core window system initialisation.
        //
        System.out.println("EGLDrawableFactory.createWindow()");
        if (!createWindow()) {
            throw new GLException("KD window init failed");
        }
        //        System.out.println("EGLDrawableFactory.setWindowVisible()");
        //        setWindowVisible();
        System.out.println("EGLDrawableFactory.setWindowFullscreen()");
        setWindowFullscreen();
        System.out.println("EGLDrawableFactory.realizeWindow()");
        if (!realizeWindow()) {
            throw new GLException("EGL/GLES window realize failed");
        }
        System.out.println("EGLDrawableFactory.createSurface()");
        if (!createSurface()) {
            throw new GLException("EGL create window surface failed");
        }
        System.out.println("EGLDrawableFactory.makeCurrent()");
        if (!makeCurrent()) {
            throw new GLException("EGL make current failed");
        }
        System.out.println("EGLDrawableFactory.updateWindowSize()");
        updateWindowSize();
    }

    */

    /*

    // Process incoming events -- must be called every frame
    public void processEvents() {
        if (shouldExit()) {
            shutdown();
        }
    }

    public void swapBuffers() {
        swapBuffers0();
    }

    private native boolean initEGL();
    private native boolean chooseConfig();
    private native boolean checkDisplay();
    private native boolean checkConfig();
    private native boolean createWindow();
    private native void    setWindowVisible();
    private native void    setWindowFullscreen();
    private native boolean realizeWindow();
    private native boolean createSurface();
    private native boolean createContext();
    private native boolean makeCurrent();
    private native void    updateWindowSize();
    private native void    swapBuffers0();

    // Runs the native message loop one step and checks to see if we should exit
    private native boolean shouldExit();
    public native void shutdown();

    public void testGetDirectBufferAddress() {
        java.nio.FloatBuffer buf = com.sun.opengl.util.BufferUtil.newFloatBuffer(12);
        int addr = getDirectBufferAddress(buf);
        System.out.println("Direct FloatBuffer's address: 0x" + Integer.toHexString(addr));
    }
    public native int getDirectBufferAddress(java.nio.Buffer buf);

    */

    public GLContext createContextOnJava2DSurface(Object graphics, GLContext shareWith)
        throws GLException {
        throw new GLUnsupportedException("Unimplemented on this platform");
    }
}
