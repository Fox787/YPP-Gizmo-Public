package main.java.general;

import java.util.List;

public class DRGizmoUtil {
	
	
	public static int[] ToIntArray(List<Integer> list)  {
	    int[] ret = new int[list.size()];
	    int i = 0;
	    for (Integer e : list)  
	        ret[i++] = e;
	    return ret;
	}
	

}
