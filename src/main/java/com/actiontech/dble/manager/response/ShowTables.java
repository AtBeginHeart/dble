/*
 * Copyright (C) 2016-2020 ActionTech.
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher.
 */

package com.actiontech.dble.manager.response;

import com.actiontech.dble.backend.mysql.PacketUtil;
import com.actiontech.dble.config.ErrorCode;
import com.actiontech.dble.config.Fields;
import com.actiontech.dble.manager.ManagerConnection;
import com.actiontech.dble.manager.handler.PackageBufINf;
import com.actiontech.dble.manager.information.ManagerSchemaInfo;
import com.actiontech.dble.net.mysql.EOFPacket;
import com.actiontech.dble.net.mysql.FieldPacket;
import com.actiontech.dble.net.mysql.ResultSetHeaderPacket;
import com.actiontech.dble.net.mysql.RowDataPacket;
import com.actiontech.dble.server.response.ShowTablesStmtInfo;
import com.actiontech.dble.util.StringUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ShowTables {
    private ShowTables() {
    }

    public static void execute(ManagerConnection c, String stmt) {
        ShowTablesStmtInfo info;
        try {
            info = new ShowTablesStmtInfo(stmt);
        } catch (Exception e) {
            c.writeErrMessage(ErrorCode.ER_PARSE_ERROR, e.toString());
            return;
        }
        if (info.isFull() || info.isAll()) {
            c.writeErrMessage("42000", "not allow All clause in statement", ErrorCode.ER_PARSE_ERROR);
            return;
        }
        if (info.getWhere() != null) {
            c.writeErrMessage("42000", "only allow LIKE clause in statement", ErrorCode.ER_PARSE_ERROR);
            return;
        }
        String showSchema = info.getSchema();
        if (showSchema != null) {
            showSchema = showSchema.toLowerCase();
        }
        String cSchema = showSchema == null ? c.getSchema() : showSchema;
        if (cSchema == null) {
            c.writeErrMessage("3D000", "No database selected", ErrorCode.ER_NO_DB_ERROR);
            return;
        }
        if (!cSchema.equals(ManagerSchemaInfo.SCHEMA_NAME)) {
            c.writeErrMessage("42000", "Unknown database '" + cSchema + "'", ErrorCode.ER_BAD_DB_ERROR);
            return;
        }
        responseDirect(c, cSchema, info);
    }

    private static void responseDirect(ManagerConnection c, String cSchema, ShowTablesStmtInfo info) {
        ByteBuffer buffer = c.allocate();
        PackageBufINf bufInf;
        String schemaColumn = cSchema;
        if (info.getLike() != null) {
            schemaColumn = schemaColumn + " (" + info.getLike() + ")";
        }

        bufInf = writeTablesHeaderAndRows(buffer, c, getTableSet(info), schemaColumn);

        writeRowEof(bufInf.getBuffer(), c, bufInf.getPacketId());
    }

    private static PackageBufINf writeTablesHeaderAndRows(ByteBuffer buffer, ManagerConnection c, List<String> tableNames, String cSchema) {
        int fieldCount = 1;
        ResultSetHeaderPacket header = PacketUtil.getHeader(fieldCount);
        FieldPacket[] fields = new FieldPacket[fieldCount];
        int i = 0;
        byte packetId = 0;
        header.setPacketId(++packetId);
        fields[i] = PacketUtil.getField("Tables_in_" + cSchema, Fields.FIELD_TYPE_VAR_STRING);
        fields[i].setPacketId(++packetId);

        EOFPacket eof = new EOFPacket();
        eof.setPacketId(++packetId);
        // write header
        buffer = header.write(buffer, c, true);
        // write fields
        for (FieldPacket field : fields) {
            buffer = field.write(buffer, c, true);
        }
        // write eof
        eof.write(buffer, c, true);
        for (String name : tableNames) {
            RowDataPacket row = new RowDataPacket(fieldCount);
            row.add(StringUtil.encode(name, c.getCharset().getResults()));
            row.setPacketId(++packetId);
            buffer = row.write(buffer, c, true);
        }
        PackageBufINf packBuffInfo = new PackageBufINf();
        packBuffInfo.setBuffer(buffer);
        packBuffInfo.setPacketId(packetId);
        return packBuffInfo;
    }

    private static void writeRowEof(ByteBuffer buffer, ManagerConnection c, byte packetId) {
        // write last eof
        EOFPacket lastEof = new EOFPacket();
        lastEof.setPacketId(++packetId);
        buffer = lastEof.write(buffer, c, true);

        // post write
        c.write(buffer);
    }

    private static List<String> getTableSet(ShowTablesStmtInfo info) {
        List<String> tableNames = new ArrayList<>();
        Pattern pattern = null;
        if (null != info.getLike()) {
            String p = "^" + info.getLike().replaceAll("%", ".*");
            pattern = Pattern.compile(p, Pattern.CASE_INSENSITIVE);
        }
        for (String tbName : ManagerSchemaInfo.getInstance().getTables().keySet()) {
            if ((pattern == null || pattern.matcher(tbName).matches())) {
                tableNames.add(tbName);
            }
        }
        return tableNames;
    }

}
