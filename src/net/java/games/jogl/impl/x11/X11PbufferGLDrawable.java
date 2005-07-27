/*
 * Copyright (c) 2003 Sun Microsystems, Inc. All Rights Reserved.
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
 * You acknowledge that this software is not designed or intended for use
 * in the design, construction, operation or maintenance of any nuclear
 * facility.
 * 
 * Sun gratefully acknowledges that this software was originally authored
 * and developed by Kenneth Bradley Russell and Christopher John Kline.
 */

package net.java.games.jogl.impl.x11;

import net.java.games.jogl.*;
import net.java.games.jogl.impl.*;

public class X11PbufferGLDrawable extends X11GLDrawable {
  private int  initWidth;
  private int  initHeight;

  // drawable in superclass is a GLXPbuffer
  private GLXFBConfig fbConfig;
  private int  width;
  private int  height;

  protected static final int MAX_PFORMATS = 256;
  protected static final int MAX_ATTRIBS  = 256;

  public X11PbufferGLDrawable(GLCapabilities capabilities, int initialWidth, int initialHeight) {
    super(null, capabilities, null);
    this.initWidth  = initialWidth;
    this.initHeight = initialHeight;
    if (initWidth <= 0 || initHeight <= 0) {
      throw new GLException("Initial width and height of pbuffer must be positive (were (" +
			    initWidth + ", " + initHeight + "))");
    }

    if (DEBUG) {
      System.out.println("Pbuffer caps on init: " + capabilities +
                         (capabilities.getOffscreenRenderToTexture() ? " [rtt]" : "") +
                         (capabilities.getOffscreenRenderToTextureRectangle() ? " [rect]" : "") +
                         (capabilities.getOffscreenFloatingPointBuffers() ? " [float]" : ""));
    }

    createPbuffer(X11GLDrawableFactory.getDisplayConnection());
  }

  public GLContext createContext(GLContext shareWith) {
    return new X11PbufferGLContext(this, shareWith);
  }

  public void destroy() {
    lockAWT();
    if (drawable != 0) {
      GLX.glXDestroyPbuffer(display, drawable);
    }
    unlockAWT();
    display = 0;
  }

  public void setSize(int width, int height) {
    // FIXME
    throw new GLException("Not yet implemented");
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public void createPbuffer(long display) {
    lockAWT();
    try {
      if (display == 0) {
        throw new GLException("Null display");
      }
    
      if (capabilities.getOffscreenRenderToTexture()) {
        throw new GLException("Render-to-texture pbuffers not supported yet on X11");
      }

      if (capabilities.getOffscreenRenderToTextureRectangle()) {
        throw new GLException("Render-to-texture-rectangle pbuffers not supported yet on X11");
      }

      int[]   iattributes = new int  [2*MAX_ATTRIBS];
      float[] fattributes = new float[2*MAX_ATTRIBS];
      int nfattribs = 0;
      int niattribs = 0;

      // Since we are trying to create a pbuffer, the GLXFBConfig we
      // request (and subsequently use) must be "p-buffer capable".
      iattributes[niattribs++] = GLXExt.GLX_DRAWABLE_TYPE;
      iattributes[niattribs++] = GLXExt.GLX_PBUFFER_BIT;

      iattributes[niattribs++] = GLXExt.GLX_RENDER_TYPE;
      iattributes[niattribs++] = GLXExt.GLX_RGBA_BIT;

      iattributes[niattribs++] = GLX.GLX_DOUBLEBUFFER;
      if (capabilities.getDoubleBuffered()) {
        iattributes[niattribs++] = GL.GL_TRUE;
      } else {
        iattributes[niattribs++] = GL.GL_FALSE;
      }

      iattributes[niattribs++] = GLX.GLX_DEPTH_SIZE;
      iattributes[niattribs++] = capabilities.getDepthBits();

      iattributes[niattribs++] = GLX.GLX_RED_SIZE;
      iattributes[niattribs++] = capabilities.getRedBits();

      iattributes[niattribs++] = GLX.GLX_GREEN_SIZE;
      iattributes[niattribs++] = capabilities.getGreenBits();

      iattributes[niattribs++] = GLX.GLX_BLUE_SIZE;
      iattributes[niattribs++] = capabilities.getBlueBits();

      iattributes[niattribs++] = GLX.GLX_ALPHA_SIZE;
      iattributes[niattribs++] = capabilities.getAlphaBits();

      if (capabilities.getStencilBits() > 0) {
        iattributes[niattribs++] = GLX.GLX_STENCIL_SIZE;
        iattributes[niattribs++] = capabilities.getStencilBits();
      }

      if (capabilities.getAccumRedBits()   > 0 ||
          capabilities.getAccumGreenBits() > 0 ||
          capabilities.getAccumBlueBits()  > 0) {
        iattributes[niattribs++] = GLX.GLX_ACCUM_RED_SIZE;
        iattributes[niattribs++] = capabilities.getAccumRedBits();
        iattributes[niattribs++] = GLX.GLX_ACCUM_GREEN_SIZE;
        iattributes[niattribs++] = capabilities.getAccumGreenBits();
        iattributes[niattribs++] = GLX.GLX_ACCUM_BLUE_SIZE;
        iattributes[niattribs++] = capabilities.getAccumBlueBits();
      }

      int screen = 0; // FIXME: provide way to specify this?

      if (capabilities.getOffscreenFloatingPointBuffers()) {
        String glXExtensions = GLX.glXQueryExtensionsString(display, screen);
        if (glXExtensions == null ||
            glXExtensions.indexOf("GLX_NV_float_buffer") < 0) {
          throw new GLException("Floating-point pbuffers on X11 currently require NVidia hardware");
        }
        iattributes[niattribs++] = GLX.GLX_FLOAT_COMPONENTS_NV;
        iattributes[niattribs++] = GL.GL_TRUE;
      }

      // FIXME: add FSAA support? Don't want to get into a situation
      // where we have to retry the glXChooseFBConfig call if it fails
      // due to a lack of an antialiased visual...

      iattributes[niattribs++] = 0; // null-terminate

      int[] nelementsTmp = new int[1];
      GLXFBConfig[] fbConfigs = GLX.glXChooseFBConfig(display, screen, iattributes, 0, nelementsTmp, 0);
      if (fbConfigs == null || fbConfigs.length == 0 || fbConfigs[0] == null) {
        throw new GLException("pbuffer creation error: glXChooseFBConfig() failed");
      }
      // Note that we currently don't allow selection of anything but
      // the first GLXFBConfig in the returned list
      GLXFBConfig fbConfig = fbConfigs[0];
      int nelements = nelementsTmp[0];
      if (nelements <= 0) {
        throw new GLException("pbuffer creation error: couldn't find a suitable frame buffer configuration");
      }

      if (DEBUG) {
        System.err.println("Found " + fbConfigs.length + " matching GLXFBConfigs");
        System.err.println("Parameters of default one:");
        System.err.println("render type: 0x" + Integer.toHexString(queryFBConfig(display, fbConfig, GLX.GLX_RENDER_TYPE)));
        System.err.println("rgba: " + ((queryFBConfig(display, fbConfig, GLX.GLX_RENDER_TYPE) & GLX.GLX_RGBA_BIT) != 0));
        System.err.println("r: " + queryFBConfig(display, fbConfig, GLX.GLX_RED_SIZE));
        System.err.println("g: " + queryFBConfig(display, fbConfig, GLX.GLX_GREEN_SIZE));
        System.err.println("b: " + queryFBConfig(display, fbConfig, GLX.GLX_BLUE_SIZE));
        System.err.println("a: " + queryFBConfig(display, fbConfig, GLX.GLX_ALPHA_SIZE));
        System.err.println("depth: " + queryFBConfig(display, fbConfig, GLX.GLX_DEPTH_SIZE));
        System.err.println("double buffered: " + queryFBConfig(display, fbConfig, GLX.GLX_DOUBLEBUFFER));
      }

      // Create the p-buffer.
      niattribs = 0;

      iattributes[niattribs++] = GLXExt.GLX_PBUFFER_WIDTH;
      iattributes[niattribs++] = initWidth;
      iattributes[niattribs++] = GLXExt.GLX_PBUFFER_HEIGHT;
      iattributes[niattribs++] = initHeight;

      iattributes[niattribs++] = 0;

      long tmpBuffer = GLX.glXCreatePbuffer(display, fbConfig, iattributes, 0);
      if (tmpBuffer == 0) {
        // FIXME: query X error code for detail error message
        throw new GLException("pbuffer creation error: glXCreatePbuffer() failed");
      }

      // Set up instance variables
      this.display = display;
      drawable = tmpBuffer;
      this.fbConfig = fbConfig;

      // Determine the actual width and height we were able to create.
      int[] tmp = new int[1];
      GLX.glXQueryDrawable(display, drawable, GLXExt.GLX_WIDTH, tmp, 0);
      width = tmp[0];
      GLX.glXQueryDrawable(display, drawable, GLXExt.GLX_HEIGHT, tmp, 0);
      height = tmp[0];

      if (DEBUG) {
        System.err.println("Created pbuffer " + width + " x " + height);
      }
    } finally {
      unlockAWT();
    }
  }

  public int getFloatingPointMode() {
    // Floating-point pbuffers currently require NVidia hardware on X11
    return GLPbuffer.NV_FLOAT;
  }
  
  public GLXFBConfig getFBConfig() {
    return fbConfig;
  }

  private int queryFBConfig(long display, GLXFBConfig fbConfig, int attrib) {
    int[] tmp = new int[1];
    if (GLX.glXGetFBConfigAttrib(display, fbConfig, attrib, tmp, 0) != 0) {
      throw new GLException("glXGetFBConfigAttrib failed");
    }
    return tmp[0];
  }
}
