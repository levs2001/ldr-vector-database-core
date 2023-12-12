package server.pool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsistentHashing implements IWorkersHashing {
    private static final Logger log = LoggerFactory.getLogger(ConsistentHashing.class);

    private static final int DOMAINS_COUNT = 256;
    private static final int V_NODES_FACTOR = 3;
    private static final String HASH_SUFFIX = "abcdefghijklmnoprstuvwxyz";

    private final TreeMap<Integer, String> circle;

    public ConsistentHashing(List<String> workers) {
        this.circle = new TreeMap<>();
        for (String worker : workers) {
            addWorker(worker);
        }
    }

    @Override
    public List<Integer> addWorker(String worker) {
        int[] borderDomains = getWorkerBorderDomains(worker);
        for (int domain : borderDomains) {
            circle.put(domain, worker);
        }
        log.info("Circle border domains for new worker: {}", Arrays.toString(borderDomains));

        List<Integer> result = new ArrayList<>();
        // TODO: Не оптимально, наверное, можно проще?
        for (int domain = 0; domain < DOMAINS_COUNT; domain++) {
            if (getWorker(domain).equals(worker)) {
                result.add(domain);
            }
        }

        log.info("Added new worker: {}. It responsible for domains: {}", worker, result);
        return result;
    }

    // TODO: worker выбывший из ротации и потом вошедший с непочищенными данными все сломает.
    @Override
    public void deleteWorker(String worker) {
        int[] borderDomains = getWorkerBorderDomains(worker);
        for (int domain : borderDomains) {
            String responsibleWorker = circle.get(domain);
            if (responsibleWorker.equals(worker)) {
                circle.remove(domain);
            } else {
                log.error("Error deleting worker circle another worker responsible for this domain, " +
                        "domain: {}, deleting worker: {}, responsible worker: {}", domain, worker, responsibleWorker);
            }
        }
        log.info("Circle border domains for new worker: {}", Arrays.toString(borderDomains));

    }

    private int[] getWorkerBorderDomains(String worker) {
        int[] result = new int[V_NODES_FACTOR];

        // TODO: This type of hashing with strings can be bad for real systems, use murmur or smth cool.
        StringBuilder virtualNodeHashStr = new StringBuilder();
        for (int i = 0; i < V_NODES_FACTOR; i++) {
            virtualNodeHashStr.append(worker).append(HASH_SUFFIX);
            String virtualNode = virtualNodeHashStr.append(i).toString();
            result[i] = getDomain(virtualNode.hashCode());
        }

        return result;
    }

    @Override
    public Owner getOwner(long id, String collection) {
        int domain = getDomain(id);

        String worker = getWorker(domain);
        String internalCollection = collection + domain;

        return new Owner(worker, internalCollection);
    }

    private String getWorker(int domain) {
        // ближайший домен в кольце (воркер этого домена отвечает и за предыдущие)
        Integer nextDomain = circle.ceilingKey(domain);
        if (nextDomain == null) {
            // выбираем первый в кольце
            nextDomain = circle.firstKey();
        }

        return circle.get(nextDomain);
    }

    private int getDomain(long num) {
        // To avoid hash below zero.
        return Math.abs((int) num % DOMAINS_COUNT);
    }
}
