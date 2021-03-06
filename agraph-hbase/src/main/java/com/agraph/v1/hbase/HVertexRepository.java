package com.agraph.v1.hbase;

import com.agraph.common.concurrent.FutureHelper;
import com.agraph.common.util.DateTimes;
import com.agraph.common.util.Iterables2;
import com.agraph.common.util.Strings;
import com.agraph.common.util.Systems;
import com.agraph.config.Config;
import com.agraph.v1.Vertex;
import com.agraph.v1.repository.VertexRepository;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * TODO: Class description here.
 *
 * @author <a href="https://github.com/tjeubaoit">tjeubaoit</a>
 */
public class HVertexRepository extends BaseRepository implements VertexRepository {

    public HVertexRepository(Config conf) {
        super(conf);
    }

    @Override
    public Iterable<Vertex> findAll() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Vertex findOne(Vertex entity) {
        return findOne(entity.label(), entity.id());
    }

    @Override
    public Vertex findOne(String label, String id) {
        try (Table table = connection.getTable(getTableName(Constants.TB_VERTEX))) {
            byte[] row = buildRowKey(id, label);
            Get get = new Get(row);
            Result r = table.get(get);
            if (r.isEmpty()) return null;

            Map<String, Object> props = new HashMap<>();
            r.getFamilyMap(CF).forEach((k, v) -> {
                if (HBaseUtils.startsWith(k, Constants.SYSTEM_PREFIX)) return;
                props.put(Bytes.toString(k), Bytes.toString(v));
            });

            return Vertex.create(id, label, props);
        } catch (NullPointerException | IOException e) {
            throw new HBaseRuntimeException(e);
        }
    }

    @Override
    public Iterable<Vertex> findByLabel(String label) {
        List<Iterable<Vertex>> listVertices = new ArrayList<>(HBaseUtils.DEFAULT_MAX_BUCKET);
        try (Table table = connection.getTable(getTableName(Constants.TB_VERTEX))){
            for (int i = 0; i < HBaseUtils.DEFAULT_MAX_BUCKET; i++) {
                byte[] bucket = String.valueOf(i).getBytes();
                byte[] rowPrefix = HBaseUtils.createCompositeKey(bucket, label.getBytes());

                Scan scan = new Scan();
                scan.setRowPrefixFilter(rowPrefix);
                ResultScanner scanner = table.getScanner(scan);

                listVertices.add(Iterables2.transform(scanner, r -> {
                    Map<String, Object> props = new HashMap<>();
                    r.getFamilyMap(CF).forEach((k, v) -> props.put(Bytes.toString(k), Bytes.toString(v)));

                    byte[] row = HBaseUtils.extractCompositeKeys(r.getRow()).get(2).array();
                    String id = new String(row);

                    return Vertex.create(id, label, props);
                }));
            }
            return com.google.common.collect.Iterables.concat(listVertices);
        } catch (IOException e) {
            throw new HBaseRuntimeException(e);
        }
    }

    @Override
    public ListenableFuture<Iterable<Vertex>> delete(Vertex entity) {
        Future<?> fut = execute("DeleteVertex\"DeleteEdge\", ", () -> {
            try (Table table = connection.getTable(getTableName(Constants.TB_VERTEX))) {
                byte[] row = buildRowKey(entity.id(), entity.label());
                Delete delete = new Delete(row);
                table.delete(delete);
            } catch (NullPointerException | IOException e) {
                throw new HBaseRuntimeException(e);
            }
        });
        return FutureHelper.transform(fut, o -> Collections.singleton(entity));
    }

    @Override
    public ListenableFuture<Iterable<Vertex>> save(Vertex entity) {
        return saveAll(Collections.singleton(entity));
    }

    @Override
    public ListenableFuture<Iterable<Vertex>> saveAll(Collection<Vertex> entities) {
        Future<?> fut = execute("SaveVertices", () -> {
            try (Table table = connection.getTable(getTableName(Constants.TB_VERTEX))) {
                List<Put> puts = new ArrayList<>(entities.size());
                for (Vertex entity : entities) {
                    puts.add(createVertexPut(entity));
                }
                table.put(puts);
            } catch (NullPointerException | IOException e) {
                throw new HBaseRuntimeException(e);
            }
        });
        return FutureHelper.transform(fut, o -> entities);
    }

    private static byte[] buildRowKey(String vertexId, String label) {
        return HBaseUtils.buildCompositeKeyWithBucket(
                vertexId, // seed
                label.getBytes(),
                vertexId.getBytes());
    }

    private static Put createVertexPut(Vertex vertex) {
        byte[] row = buildRowKey(vertex.id(), vertex.label());

        Put put = new Put(row);
        put.addColumn(CF, Constants.CQ_CREATED_DATE, Systems.inverseTimestamp(), DateTimes.currentDateAsBytes());
        put.addColumn(CF, CQ_HIDDEN, HBaseUtils.EMPTY);

        vertex.properties().forEach((k, v) -> {
            if (Strings.isNullOrStringEmpty(v)) return;
            put.addColumn(CF, k.getBytes(), String.valueOf(v).getBytes());
        });

        return put;
    }
}
