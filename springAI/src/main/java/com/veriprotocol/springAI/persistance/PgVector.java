package com.veriprotocol.springAI.persistance;

public final class PgVector {

	private PgVector() {}

    public static String toLiteral(float[] v) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
				sb.append(',');
			}
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
