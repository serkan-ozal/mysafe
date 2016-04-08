/*
 * Copyright (c) 1986-2016, Serkan OZAL, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tr.com.serkanozal.mysafe.impl;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;

import tr.com.serkanozal.mysafe.impl.callerinfo.CallerInfo;
import tr.com.serkanozal.mysafe.impl.callerinfo.CallerInfoAllocatedMemory;
import tr.com.serkanozal.mysafe.impl.callerinfo.CallerInfo.CallerInfoEntry;

class CallerPathDiagramGenerator {

    static final String DEFAULT_DIAGRAM_NANE = "mysafe-caller-path.png";
    
    private static final Color CALLER_INFO_FRAME_BACKGROUND_COLOR = new Color(255, 228, 200);
    private static final Color CALLER_INFO_UNIT_BACKGROUND_COLOR = new Color(228, 255, 255);
    
    private static final int CALLER_INFO_FONT_SIZE = 14;
    private static final int CALLER_INFO_H_GAP = 8;
    private static final int CALLER_INFO_V_GAP = 8;
    
    private static final int CALLER_INFO_UNIT_WIDTH = 800;
    private static final int CALLER_INFO_UNIT_H_GAP = 50;
    private static final int CALLER_INFO_FRAME_WIDTH = CALLER_INFO_UNIT_WIDTH + (2 * CALLER_INFO_UNIT_H_GAP);
    
    private static final int CALLER_INFO_UNIT_HEIGHT = CALLER_INFO_FONT_SIZE + (2 * CALLER_INFO_V_GAP);
    private static final int CALLER_INFO_UNIT_V_GAP = 50;
    private static final int CALLER_INFO_FRAME_HEIGTH = 
                    ((CallerInfo.MAX_CALLER_DEPTH + 1) * CALLER_INFO_UNIT_HEIGHT)
                    +
                    ((CallerInfo.MAX_CALLER_DEPTH + 1 + 1) * CALLER_INFO_UNIT_V_GAP);       

    static void generateCallerPathDiagram(String diagramName, 
                                          List<CallerInfoAllocatedMemory> callerInfoAllocatedMemories) {
        try {
            if (!diagramName.endsWith(".png")) {
                diagramName = diagramName + ".png";
            }
            final int callerInfoCount = callerInfoAllocatedMemories.size();
            final int CALLER_INFO_FRAME_H_GAP = 100;
            final int DIAGRAM_WIDTH = CALLER_INFO_FRAME_WIDTH + (2 * CALLER_INFO_FRAME_H_GAP);
            final int CALLER_INFO_FRAME_V_GAP = 100;
            final int DIAGRAM_HEIGTH = 
                            (callerInfoCount * CALLER_INFO_FRAME_HEIGTH)
                            +
                            ((callerInfoCount + 1) * CALLER_INFO_FRAME_V_GAP);
            
            BufferedImage img = new BufferedImage(DIAGRAM_WIDTH, DIAGRAM_HEIGTH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = img.createGraphics();
            Font font = new Font("Arial", Font.PLAIN | Font.BOLD, CALLER_INFO_FONT_SIZE);
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.setColor(Color.BLACK);
            
            for (int i = 0; i < callerInfoCount; i++) {
                CallerInfoAllocatedMemory callerInfoAllocatedMemory = callerInfoAllocatedMemories.get(i);
                CallerInfo callerInfo = callerInfoAllocatedMemory.callerInfo;
                long allocatedMemory = callerInfoAllocatedMemory.allocatedMemory;
                
                int x1 = CALLER_INFO_FRAME_H_GAP;
                int y1 = ((i + 1) * CALLER_INFO_FRAME_V_GAP) + (i * CALLER_INFO_FRAME_HEIGTH) ;
                g2d.setColor(CALLER_INFO_FRAME_BACKGROUND_COLOR);
                g2d.fillRoundRect(x1, y1, CALLER_INFO_FRAME_WIDTH, CALLER_INFO_FRAME_HEIGTH, 20, 20);
                g2d.setColor(Color.BLACK);
                g2d.drawRoundRect(x1, y1, CALLER_INFO_FRAME_WIDTH, CALLER_INFO_FRAME_HEIGTH, 20, 20);
                
                int x2 = x1 + CALLER_INFO_UNIT_H_GAP;
                int y2 = y1 + CALLER_INFO_UNIT_V_GAP + CALLER_INFO_V_GAP + CALLER_INFO_FONT_SIZE;
                g2d.drawString("Allocated memory: " + generateAllocatedMemoryString(allocatedMemory), x2, y2);
                
                List<CallerInfoEntry> callerInfoEntries = callerInfo.callerInfoEntries;
                for (int j = 0; j < callerInfoEntries.size(); j++) {
                    CallerInfoEntry callerInfoEntry = callerInfoEntries.get(j);
                    
                    int x3 = x1 + CALLER_INFO_UNIT_H_GAP;
                    int y3 = y1 + ((j + 2) * CALLER_INFO_UNIT_V_GAP) + ((j + 1) * CALLER_INFO_UNIT_HEIGHT);
                    g2d.setColor(CALLER_INFO_UNIT_BACKGROUND_COLOR);
                    g2d.fillRect(x3, y3, CALLER_INFO_UNIT_WIDTH, CALLER_INFO_UNIT_HEIGHT);
                    g2d.setColor(Color.BLACK);
                    g2d.drawRect(x3, y3, CALLER_INFO_UNIT_WIDTH, CALLER_INFO_UNIT_HEIGHT);
                    
                    if (j > 0) {
                        int x4a = x3 + (CALLER_INFO_UNIT_WIDTH / 2);
                        int y4a = y3 - CALLER_INFO_UNIT_V_GAP;
                        int x4b = x4a;
                        int y4b = y4a + CALLER_INFO_UNIT_V_GAP;
                        g2d.drawLine(x4a, y4a, x4b, y4b);
                        
                        int[] xPoints = new int[] {x4b - 10, x4b, x4b + 10};
                        int[] yPoints = new int[] {y4b - 20, y4b, y4b - 20};
                        g2d.fillPolygon(xPoints, yPoints, 3);
                    }
                    
                    String callerInfoUnit = callerInfoEntry.toString();
                    int callerInfoUnitTextWidth = fm.stringWidth(callerInfoUnit);
                    int x5 = x3 + CALLER_INFO_H_GAP + ((CALLER_INFO_UNIT_WIDTH - callerInfoUnitTextWidth) / 2);
                    int y5 = y3 + CALLER_INFO_V_GAP + CALLER_INFO_FONT_SIZE;
                    g2d.drawString(callerInfoUnit, x5, y5);
                }
            }
            
            g2d.dispose();
            ImageIO.write(img, "png", new File(diagramName));
        } catch (Throwable t) {
            throw new RuntimeException("Caller path diagram couldn't be generated!", t);
        }
    }
    
    private static String generateAllocatedMemoryString(long allocatedMemory) {
        StringBuilder sb = new StringBuilder();
        long memory = allocatedMemory;
        long gb = memory / (1024 * 1024 * 1024);
        memory = memory - (gb * 1024 * 1024 * 1024);
        long mb = memory / (1024 * 1024);
        memory = memory - (mb * 1024 * 1024);
        long kb = memory / (1024);
        memory = memory - (kb * 1024);
        long b = memory;
        
        boolean appended = false;
        if (gb > 0) {
            sb.append(gb).append(" GB");
            appended = true;
        }
        if (mb > 0) {
            if (appended) {
                sb.append(", ");
            }
            sb.append(mb).append(" MB");
            appended = true;
        }
        if (kb > 0) {
            if (appended) {
                sb.append(", ");
            }
            sb.append(kb).append(" KB");
            appended = true;
        }
        if (b > 0) {
            if (appended) {
                sb.append(", ");
            }
            sb.append(b).append(" byte");
            appended = true;
        }
        return sb.toString();
    }
    
}
