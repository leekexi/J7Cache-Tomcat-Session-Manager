package com.j7.cache;

import java.io.UnsupportedEncodingException;

import com.j7.jdbc.driver.Connection;
import com.j7.jdbc.driver.ResultSet;
import com.j7.jdbc.driver.Statement;

public class J7Manager {

	public static void main(String[] args) {

	}

	public Connection conn = new Connection();
	public String database = "";

	public J7Manager(String host, int port, String db) {
		this.database = db;
		String url = "jdbc:j7cache://" + host + ":" + port + ":user=root:password=123456";
		conn.setURL(url);
	}

	public void close() {
		conn.close();
	}

	public void createSessionDataBase(String db) {
		String SQL = "CREATE TABLE " + db;
		Statement stmt = conn.createStatement();
		stmt.executeUpdate(SQL);
		stmt.close();
	}

	public void dropSessionDataBase(String db) {
		String SQL = "DROP TABLE " + db;
		Statement stmt = conn.createStatement();
		stmt.executeUpdate(SQL);
		stmt.close();
	}

	public void createSessionMaxInactiveInterval() {
		String SQL = "CREATE TABLE MaxInactiveInterval";
		Statement stmt = conn.createStatement();
		stmt.executeUpdate(SQL);
		stmt.close();
	}

	public void dropSessionMaxInactiveInterval() {
		String SQL = "DROP TABLE MaxInactiveInterval";
		Statement stmt = conn.createStatement();
		stmt.executeUpdate(SQL);
		stmt.close();
	}

	public void clear(String db) {
		dropSessionDataBase(db);
		dropSessionMaxInactiveInterval();
		createSessionDataBase(db);
		createSessionMaxInactiveInterval();
	}

	public boolean remove_a_session(String sessionid) {
		String SQL = "DELETE FROM " + database + " WHERE KEY=" + sessionid;
		Statement stmt = conn.createStatement();
		String rs = stmt.executeUpdate(SQL);
		stmt.close();
		if (rs.indexOf("Deleted successfully") != -1) {
			SQL = "DELETE FROM MaxInactiveInterval WHERE KEY=" + sessionid;
			stmt = conn.createStatement();
			rs = stmt.executeUpdate(SQL);
			stmt.close();
			return true;
		} else {
			return false;
		}
	}

	private String insert_a_session(String se_data, String sessionid) throws UnsupportedEncodingException {
		String SQL = "INSERT INTO " + database + "(KEY,SESSION_VALUE) VALUES(" + sessionid + "," + se_data + ")";
		Statement stmt = conn.createStatement();
		String rs = stmt.executeUpdate(SQL);
		stmt.close();
		return rs;
	}

	private String update_a_session(String se_data, String sessionid) throws UnsupportedEncodingException {
		String SQL = "UPDATE " + database + " SET SESSION_VALUE=" + se_data + " WHERE KEY=" + sessionid;
		Statement stmt = conn.createStatement();
		String rs = stmt.executeUpdate(SQL);
		stmt.close();
		return rs;
	}

	public boolean save_or_update_a_session(byte[] se_data, String sessionid) {
		String SQL = "SELECT KEY FROM " + database + " WHERE KEY=" + sessionid;
		Statement stmt = conn.createStatement();
		ResultSet rsset = stmt.executeQuery(SQL);

		String rs = "";
		boolean suf = false;
		String data = bytesToHexString(se_data);
		try {
			if (rsset.size() > 0) {

				rs = update_a_session(data, sessionid);

				if (rs.indexOf("Updated successfully") != -1) {
					long timeNow = System.currentTimeMillis();
					SQL = "UPDATE MaxInactiveInterval SET ACC_TIME=" + timeNow + " WHERE KEY=" + sessionid;
					stmt = conn.createStatement();
					rs = stmt.executeUpdate(SQL);

					suf = true;
				}
			} else {
				rs = insert_a_session(data, sessionid);
				if (rs.indexOf("Data inserted successfully") != -1) {

					long timeNow = System.currentTimeMillis();
					SQL = "INSERT INTO MaxInactiveInterval(KEY,ACC_TIME) VALUES(" + sessionid + "," + timeNow + ")";
					stmt = conn.createStatement();
					rs = stmt.executeUpdate(SQL);

					suf = true;
				}
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} finally {
			if (rsset != null) {
				rsset.close();
			}

			stmt.close();
		}
		return suf;
	}

	public byte[] load_a_session(String sessionid) {

		String sql = "SELECT SESSION_VALUE FROM " + database + " WHERE KEY=" + sessionid;
		Statement stmt = conn.createStatement();
		ResultSet rsset = stmt.executeQuery(sql);

		String se = null;
		byte[] se_data = null;
		while (rsset.next() != null) {
			se = rsset.getString("SESSION_VALUE");
		}
		if (se != null) {
			se_data = hexStringToBytes(se);// .getBytes();

			long timeNow = System.currentTimeMillis();
			sql = "UPDATE MaxInactiveInterval SET ACC_TIME=" + timeNow + " WHERE KEY=" + sessionid;
			stmt = conn.createStatement();
			stmt.executeUpdate(sql);
		}

		rsset.close();
		stmt.close();
		return se_data;

	}

	public String[] get_all_session_id() {
		String SQL = "SELECT KEY FROM " + database;
		Statement stmt = conn.createStatement();
		ResultSet rsset = stmt.executeQuery(SQL);
		String[] keys = new String[rsset.size()];
		int i = 0;
		while (rsset.next() != null) {
			keys[i] = rsset.getString("KEY");
			i++;
		}
		stmt.close();
		rsset.close();
		return keys;
	}

	public ResultSet get_all_MaxInactiveInterval() {
		String SQL = "SELECT KEY,ACC_TIME FROM MaxInactiveInterval";
		Statement stmt = conn.createStatement();
		ResultSet rsset = stmt.executeQuery(SQL);
		stmt.close();
		return rsset;
	}

	/**
	 * Convert byte[] to hex string.这里我们可以将byte转换成int，然后利用Integer.toHexString(int)来转换成16进制字符串。
	 * 
	 * @param src byte[] data
	 * @return hex string
	 */
	public String bytesToHexString(byte[] src) {
		StringBuilder stringBuilder = new StringBuilder("");
		if ((src == null) || (src.length <= 0)) {
			return null;
		}
		for (byte element : src) {
			int v = element & 0xFF;
			String hv = Integer.toHexString(v);
			if (hv.length() < 2) {
				stringBuilder.append(0);
			}
			stringBuilder.append(hv);
		}
		return stringBuilder.toString();
	}

	/**
	 * Convert hex string to byte[]
	 * 
	 * @param hexString the hex string
	 * @return byte[]
	 */
	public byte[] hexStringToBytes(String hexString) {
		if ((hexString == null) || hexString.equals("")) {
			return null;
		}
		hexString = hexString.toUpperCase();
		int length = hexString.length() / 2;
		char[] hexChars = hexString.toCharArray();
		byte[] d = new byte[length];
		for (int i = 0; i < length; i++) {
			int pos = i * 2;
			d[i] = (byte) ((charToByte(hexChars[pos]) << 4) | charToByte(hexChars[pos + 1]));
		}
		return d;
	}

	/**
	 * Convert char to byte
	 * 
	 * @param c char
	 * @return byte
	 */
	private byte charToByte(char c) {
		return (byte) "0123456789ABCDEF".indexOf(c);
	}
}
