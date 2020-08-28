package com.jetbrains.python.console.pydev;

import com.intellij.icons.AllIcons;
import consulo.ui.image.Image;

import javax.annotation.Nullable;

public class PyCodeCompletionImages {

    /**
     * Returns an image for the given type
     * @param type
     * @return
     */
    @Nullable
    public static Image getImageForType(int type){
      switch (type) {
        case IToken.TYPE_CLASS:
          return AllIcons.Nodes.Class;
        case IToken.TYPE_FUNCTION:
          return AllIcons.Nodes.Method;
        default:
          return null;
      }
    }

}
