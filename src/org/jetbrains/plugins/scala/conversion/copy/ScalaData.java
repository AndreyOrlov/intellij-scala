package org.jetbrains.plugins.scala.conversion.copy;

import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

/**
 * Pavel Fatin
 */
public class ScalaData implements TextBlockTransferableData, Cloneable, Serializable {
  private final ReferenceData[] myReferenceDatas;

  public ScalaData(final ReferenceData[] referenceDatas) {
    myReferenceDatas = referenceDatas;
  }

  public DataFlavor getFlavor() {
    return ReferenceData.getDataFlavor();
  }

  public int getOffsetCount() {
    return myReferenceDatas.length * 2;
  }

  public int getOffsets(final int[] offsets, int index) {
    for (ReferenceData data : myReferenceDatas) {
      offsets[index++] = data.startOffset;
      offsets[index++] = data.endOffset;
    }
    return index;
  }

  public int setOffsets(final int[] offsets, int index) {
    for (ReferenceData data : myReferenceDatas) {
      data.startOffset = offsets[index++];
      data.endOffset = offsets[index++];
    }
    return index;
  }

  public ScalaData clone() {
    ReferenceData[] newReferenceData = new ReferenceData[myReferenceDatas.length];
    for (int i = 0; i < myReferenceDatas.length; i++) {
      newReferenceData[i] = (ReferenceData)myReferenceDatas[i].clone();
    }
    return new ScalaData(newReferenceData);
  }

  public ReferenceData[] getData() {
    return myReferenceDatas;
  }

  public static class ReferenceData implements Cloneable, Serializable {
    public static @NonNls
    DataFlavor ourFlavor;

    public int startOffset;
    public int endOffset;
    public final String qClassName;
    public final String staticMemberName;

    public ReferenceData(int startOffset, int endOffset, String qClassName, String staticMemberDescriptor) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.qClassName = qClassName;
      this.staticMemberName = staticMemberDescriptor;
    }

    public Object clone() {
      try{
        return super.clone();
      }
      catch(CloneNotSupportedException e){
        throw new RuntimeException();
      }
    }

    public static DataFlavor getDataFlavor() {
      if (ourFlavor != null) {
        return ourFlavor;
      }
      try {
        ourFlavor = new DataFlavor(ReferenceData.class, "ScalaReferenceData");
      }
      catch (NoClassDefFoundError e) {
        return null;
      }
      catch (IllegalArgumentException e) {
        return null;
      }
      return ourFlavor;
    }
  }
}

