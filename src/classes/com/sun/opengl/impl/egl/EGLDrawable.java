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

import com.sun.opengl.impl.GLDrawableImpl;

import javax.media.opengl.*;

public class EGLDrawable extends GLDrawableImpl {
    private GLCapabilitiesChooser chooser;
    private long display;
    private _EGLConfig config;
    private long surface;
    private int[] tmp = new int[1];

    public EGLDrawable(EGLDrawableFactory factory,
                       NativeWindow component,
                       GLCapabilities capabilities,
                       GLCapabilitiesChooser chooser) throws GLException {
        super(factory, component, false);
        setChosenGLCapabilities(capabilities);
        this.chooser = chooser;
        surface=EGL.EGL_NO_SURFACE;

        display = EGL.eglGetDisplay((0!=component.getDisplayHandle())?component.getDisplayHandle():EGL.EGL_DEFAULT_DISPLAY);
        if (display == EGL.EGL_NO_DISPLAY) {
            throw new GLException("eglGetDisplay failed");
        }
        if (!EGL.eglInitialize(display, null, null)) {
            throw new GLException("eglInitialize failed");
        }
        int[] attrs = factory.glCapabilities2AttribList(capabilities);
        _EGLConfig[] configs = new _EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL.eglChooseConfig(display,
                                 attrs, 0,
                                 configs, 1,
                                 numConfigs, 0)) {
            throw new GLException("Graphics configuration selection (eglChooseConfig) failed");
        }
        if (numConfigs[0] == 0) {
            throw new GLException("No valid graphics configuration selected from eglChooseConfig");
        }
        config = configs[0];
    }

    public long getDisplay() {
        return display;
    }

    public void destroy() {
        setRealized(false);
        if(EGL.EGL_NO_DISPLAY!=display) {
            EGL.eglTerminate(display);
            display=EGL.EGL_NO_DISPLAY;
        }
        super.destroy();
    }

    public _EGLConfig getConfig() {
        return config;
    }

    public long getSurface() {
        return surface;
    }

    protected void setSurface() {
        if (EGL.EGL_NO_SURFACE==surface) {
            try {
                lockSurface();
                // Create the window surface
                surface = EGL.eglCreateWindowSurface(display, config, component.getWindowHandle(), null);
                if (EGL.EGL_NO_SURFACE==surface) {
                    throw new GLException("Creation of window surface (eglCreateWindowSurface) failed, component: "+component);
                }
            } finally {
              unlockSurface();
            }
        }
    }

    public GLContext createContext(GLContext shareWith) {
        return new EGLContext(this, shareWith);
    }

    public void setRealized(boolean realized) {
        if (realized) {
            // setSurface();
        } else if( surface != EGL.EGL_NO_SURFACE ) {
            // Destroy the window surface
            // FIXME: we should expose a destroy() method on
            // GLDrawable and get rid of setRealized(), instead
            // destroying and re-creating the GLDrawable associated
            // with for example a GLCanvas each time
            if (!EGL.eglDestroySurface(display, surface)) {
                throw new GLException("Error destroying window surface (eglDestroySurface)");
            }
            surface = EGL.EGL_NO_SURFACE;
        }
        super.setRealized(realized);
    }

    public void setSize(int width, int height) {
        // FIXME: anything to do here?
    }

    public int getWidth() {
        if (!EGL.eglQuerySurface(display, surface, EGL.EGL_WIDTH, tmp, 0)) {
            throw new GLException("Error querying surface width");
        }
        return tmp[0];
    }

    public int getHeight() {
        if (!EGL.eglQuerySurface(display, surface, EGL.EGL_HEIGHT, tmp, 0)) {
            throw new GLException("Error querying surface height");
        }
        return tmp[0];
    }

    public void swapBuffers() throws GLException {
        getFactory().lockToolkit();
        try {
          if (component.getSurfaceHandle() == 0) {
            if (lockSurface() == NativeWindow.LOCK_SURFACE_NOT_READY) {
              return;
            }
          }

          EGL.eglSwapBuffers(display, surface);

        } finally {
          unlockSurface();
          getFactory().unlockToolkit();
        }
    }

    public String toString() {
        return "EGLDrawable[ realized "+getRealized()+
                           ", window "+getNativeWindow()+
                           ", egl display " + display +
                           ", egl config " + config +
                           ", egl surface " + surface +
                           ", factory "+getFactory()+"]";
    }
}