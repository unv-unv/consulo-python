/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.breakpoint.*;
import consulo.execution.debug.breakpoint.XLineBreakpoint;
import consulo.execution.debug.breakpoint.XBreakpointType;


import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class AbstractLineBreakpointHandler extends XBreakpointHandler<XLineBreakpoint<XBreakpointProperties>> {
  protected final PyDebugProcess myDebugProcess;
  private final Map<XLineBreakpoint<XBreakpointProperties>, XSourcePosition> myBreakPointPositions = Maps.newHashMap();

  public AbstractLineBreakpointHandler(
    Class<? extends XBreakpointType<XLineBreakpoint<XBreakpointProperties>, ?>> breakpointTypeClass,
    PyDebugProcess debugProcess) {
    super(breakpointTypeClass);
    myDebugProcess = debugProcess;
  }

  public void reregisterBreakpoints() {
    List<XLineBreakpoint<XBreakpointProperties>> breakpoints = Lists.newArrayList(myBreakPointPositions.keySet());
    for (XLineBreakpoint<XBreakpointProperties> breakpoint : breakpoints) {
      unregisterBreakpoint(breakpoint, false);
      registerBreakpoint(breakpoint);
    }
  }

  @Override
  public void registerBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    XSourcePosition position = breakpoint.getSourcePosition();
    if (position != null) {
      myDebugProcess.addBreakpoint(myDebugProcess.getPositionConverter().convertToPython(position), breakpoint);
      myBreakPointPositions.put(breakpoint, position);
    }
  }

  @Override
  public void unregisterBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint, boolean temporary) {
    XSourcePosition position = myBreakPointPositions.get(breakpoint);
    if (position != null) {
      myDebugProcess.removeBreakpoint(myDebugProcess.getPositionConverter().convertToPython(position));
      myBreakPointPositions.remove(breakpoint);
    }
  }
}
