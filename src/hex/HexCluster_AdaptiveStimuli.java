package hex;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * HexCluster_AdaptiveStimuli.java
 *
 * Combines Euclidean MST clustering on a hexagonal grid with
 * decentralized multi-stimulus token propagation and clearing waves.
 * Includes probabilistic token walking and token consumption rules.
 * All active routing nodes are converted directly to Blue terminals.
 */
public class HexCluster_AdaptiveStimuli {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClusterFrame::new);
    }
}

/* ===================== SIMULATION STATE ===================== */

class NodeState {
    boolean stimulus = false;
    int stimulusId = -1;

    Set<Integer> awareFromStimuli = new HashSet<>();
    Set<Integer> clearingStimuli = new HashSet<>();

    boolean isAware() {
        return !awareFromStimuli.isEmpty();
    }
}

class Token {
    int posIndex; // hole index
    int hopsLeft;
    int stimulusId;

    Token(int pos, int hops, int sid) {
        this.posIndex = pos;
        this.hopsLeft = hops;
        this.stimulusId = sid;
    }
}

/* ===================== MAIN APP FRAME ===================== */

class ClusterFrame extends JFrame {
    final int hexCount = 30;
    final int gridCols = 5;
    final int gridRows = 4;
    final int hexRadius = 44;
    final int horizSpacing;
    final int vertSpacing;

    final List<Hex> hexes = new ArrayList<>();
    final List<Hole> holes = new ArrayList<>();
    final Map<String, Integer> holeIndexByXY = new HashMap<>();

    // Only one active agent set now
    final Set<Integer> blueAgents = new HashSet<>();
    final List<int[]> realizedEdges = new ArrayList<>();

    // perimeter adjacency (vertex i connected to i+1 for each hex)
    Map<Integer, List<Integer>> holeAdj = new HashMap<>();

    // --- Adaptive Stimuli Variables ---
    NodeState[] nodeStates;
    final List<Token> tokens = new ArrayList<>();
    Timer timer;

    double tokenGenProb = 0.15; // Probability a stimulus generates a token
    double walkProb = 1.00;     // Probability that token continues walking
    int tokenMaxHops = 1200;    // Increased hop limit
    int delayMs = 80;           // Simulation speed
    int nextStimulusId = 1;

    final Random rnd = new Random();
    final DrawPanel drawPanel;

    ClusterFrame() {
        super("Hex Cluster — Adaptive Stimuli Routing");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        horizSpacing = (int) Math.round(1.5 * hexRadius);
        vertSpacing = (int) Math.round(Math.sqrt(3) * hexRadius);

        buildHexGridAndHoles();

        drawPanel = new DrawPanel();
        int width = gridCols * horizSpacing + hexRadius * 2 + 120;
        int height = gridRows * vertSpacing + hexRadius * 2 + 120;
        drawPanel.setPreferredSize(new Dimension(width, height));

        // --- Buttons ---
        JButton regen = new JButton("Regenerate");
        regen.setBackground(new Color(30, 144, 255));
        regen.setForeground(Color.WHITE);
        regen.setFocusPainted(false);
        regen.addActionListener(e -> {
            randomPlaceAgents(20);
            realizedEdges.clear();
            resetSim();
            drawPanel.repaint();
        });

        JButton connect = new JButton("Connect Blues");
        connect.setBackground(new Color(46, 204, 113));
        connect.setForeground(Color.WHITE);
        connect.setFocusPainted(false);
        connect.addActionListener(e -> {
            realizeMSTWithRedundancy();
            resetSim();
            drawPanel.repaint();
        });

        JButton startPause = new JButton("Start / Pause Sim");
        startPause.setBackground(new Color(150, 150, 150));
        startPause.setForeground(Color.WHITE);
        startPause.setFocusPainted(false);
        startPause.addActionListener(e -> toggleSim());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        top.setBackground(new Color(248, 249, 250));
        top.add(regen);
        top.add(connect);
        top.add(startPause);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(drawPanel), BorderLayout.CENTER);
        add(new LegendPanel(), BorderLayout.SOUTH);

        randomPlaceAgents(40);

        // Start simulation timer
        timer = new Timer(delayMs, e -> stepSim());
        timer.start();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    void toggleSim() {
        if (timer.isRunning()) timer.stop();
        else timer.start();
    }

    void resetSim() {
        tokens.clear();
        for (int i = 0; i < holes.size(); i++) {
            nodeStates[i] = new NodeState();
        }
    }

    /* ===================== SIMULATION STEP ===================== */

    void stepSim() {
        if (holes.isEmpty() || nodeStates == null) return;

        /* ---- 1. TOKEN GENERATION ---- */
        for (int i = 0; i < holes.size(); i++) {
            if (nodeStates[i].stimulus && rnd.nextDouble() < tokenGenProb) {
                tokens.add(new Token(i, tokenMaxHops, nodeStates[i].stimulusId));
            }
        }

        /* ---- 2. TOKEN WALK ---- */
        List<Token> dead = new ArrayList<>();

        for (Token t : tokens) {
            if (t.hopsLeft <= 0) {
                dead.add(t);
                continue;
            }

            /* probabilistic continuation of walk */
            if (rnd.nextDouble() >= walkProb) {
                dead.add(t);
                continue;
            }

            // Find valid neighbors: must be adjacent AND active (Blue)
            List<Integer> validNbs = new ArrayList<>();
            for (int nb : holeAdj.get(t.posIndex)) {
                if (blueAgents.contains(nb)) {
                    validNbs.add(nb);
                }
            }

            if (validNbs.isEmpty()) {
                dead.add(t);
                continue;
            }

            // Walk to random active neighbor
            int nextNode = validNbs.get(rnd.nextInt(validNbs.size()));
            t.posIndex = nextNode;
            t.hopsLeft--;

            NodeState v = nodeStates[nextNode];

            // Absorb if this node is clearing this specific stimulus
            if (v.clearingStimuli.contains(t.stimulusId)) {
                dead.add(t);
                continue;
            }

            // Consume token upon infecting an unaware node
            if (!v.awareFromStimuli.contains(t.stimulusId)) {
                v.awareFromStimuli.add(t.stimulusId);
                dead.add(t);
            }
        }

        tokens.removeAll(dead);

        /* ---- 3. CLEARING PROPAGATION ---- */
        List<Integer> toProcess = new ArrayList<>();
        for (int i = 0; i < holes.size(); i++) {
            if (!nodeStates[i].clearingStimuli.isEmpty()) {
                toProcess.add(i);
            }
        }

        for (int uIdx : toProcess) {
            NodeState u = nodeStates[uIdx];
            Set<Integer> clearingCopy = new HashSet<>(u.clearingStimuli);

            for (int sid : clearingCopy) {
                for (int nb : holeAdj.get(uIdx)) {
                    // Only propagate through active nodes
                    if (!blueAgents.contains(nb)) continue;

                    NodeState v = nodeStates[nb];
                    if (v.awareFromStimuli.contains(sid)) {
                        v.clearingStimuli.add(sid);
                    }

                    // Erase tokens associated with this ID at this location
                    tokens.removeIf(t -> t.posIndex == nb && t.stimulusId == sid);
                }

                u.awareFromStimuli.remove(sid);
                u.clearingStimuli.remove(sid);
            }
        }

        drawPanel.repaint();
    }


    /* ===================== GRAPH BUILD & MST ===================== */

    void buildHexGridAndHoles() {
        hexes.clear();
        holes.clear();
        holeIndexByXY.clear();
        int idx = 0;
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                if (idx >= hexCount) break;
                int cx = 70 + c * horizSpacing;
                int cy = 70 + r * vertSpacing + (c % 2 == 1 ? vertSpacing / 2 : 0);
                Hex h = new Hex(idx, cx, cy, hexRadius);
                hexes.add(h);
                for (int v = 0; v < 6; v++) {
                    Point p = h.vertex(v);
                    String key = p.x + "," + p.y;
                    if (!holeIndexByXY.containsKey(key)) {
                        int holeIndex = holes.size();
                        holes.add(new Hole(h.index, v, p.x, p.y));
                        holeIndexByXY.put(key, holeIndex);
                    }
                }
                idx++;
            }
        }
        for (Hex h : hexes) {
            h.vertexHoleIndices = new int[6];
            for (int v = 0; v < 6; v++) {
                Point p = h.vertex(v);
                h.vertexHoleIndices[v] = holeIndexByXY.get(p.x + "," + p.y);
            }
        }

        nodeStates = new NodeState[holes.size()];
        for (int i = 0; i < holes.size(); i++) {
            nodeStates[i] = new NodeState();
        }

        buildPerimeterAdj();
    }

    void buildPerimeterAdj() {
        holeAdj.clear();
        for (int i = 0; i < holes.size(); i++) holeAdj.put(i, new ArrayList<>());
        for (Hex h : hexes) {
            int[] v = h.vertexHoleIndices;
            for (int i = 0; i < 6; i++) {
                int a = v[i], b = v[(i + 1) % 6];
                if (!holeAdj.get(a).contains(b)) holeAdj.get(a).add(b);
                if (!holeAdj.get(b).contains(a)) holeAdj.get(b).add(a);
            }
        }
    }

    void randomPlaceAgents(int k) {
        blueAgents.clear();
        if (holes.isEmpty()) return;
        k = Math.min(k, holes.size());
        while (blueAgents.size() < k) blueAgents.add(rnd.nextInt(holes.size()));
    }

    void realizeMSTWithRedundancy() {
        realizedEdges.clear();

        if (blueAgents.size() <= 1) return;

        List<Integer> bluesList = new ArrayList<>(blueAgents);
        int m = bluesList.size();
        List<MSEdge> allBlueEdges = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                int u = bluesList.get(i), v = bluesList.get(j);
                double w = distance(holes.get(u).x, holes.get(u).y, holes.get(v).x, holes.get(v).y);
                allBlueEdges.add(new MSEdge(u, v, w));
            }
        }
        Collections.sort(allBlueEdges);
        UnionFind uf = new UnionFind(holes.size());
        List<MSEdge> mstEdges = new ArrayList<>();
        for (MSEdge e : allBlueEdges) {
            if (uf.find(e.u) != uf.find(e.v)) {
                uf.union(e.u, e.v);
                mstEdges.add(e);
                if (mstEdges.size() == m - 1) break;
            }
        }

        for (MSEdge e : mstEdges) {
            List<Integer> path = shortestPathOnHoles(e.u, e.v, holeAdj);
            if (path != null) addPathAsEdges(path);
        }

        for (int u : bluesList) {
            PriorityQueue<MSEdge> pq = new PriorityQueue<>();
            for (int v : bluesList)
                if (v != u) {
                    double w = distance(holes.get(u).x, holes.get(u).y, holes.get(v).x, holes.get(v).y);
                    pq.add(new MSEdge(u, v, w));
                }
            int added = 0;
            while (!pq.isEmpty() && added < 2) {
                MSEdge cand = pq.poll();
                List<Integer> path = shortestPathOnHoles(cand.u, cand.v, holeAdj);
                if (path != null) {
                    addPathAsEdges(path);
                    added++;
                }
            }
        }

        // Add perimeter edges between active agents to realize them visually
        for (int a : holeAdj.keySet()) {
            for (int b : holeAdj.get(a)) {
                if (a < b && blueAgents.contains(a) && blueAgents.contains(b)) {
                    realizedEdges.add(new int[]{a, b});
                }
            }
        }

        Set<Long> seen = new HashSet<>();
        List<int[]> uniq = new ArrayList<>();
        for (int[] e : realizedEdges) {
            int a = Math.min(e[0], e[1]), b = Math.max(e[0], e[1]);
            long key = (((long) a) << 32) | (b & 0xffffffffL);
            if (!seen.contains(key)) {
                seen.add(key);
                uniq.add(new int[]{a, b});
            }
        }
        realizedEdges.clear();
        realizedEdges.addAll(uniq);
    }

    // Now simply adds any routing nodes directly to the Blue active group
    void addPathAsEdges(List<Integer> path) {
        for (int idx : path) {
            blueAgents.add(idx);
        }
        for (int i = 0; i + 1 < path.size(); i++) realizedEdges.add(new int[]{path.get(i), path.get(i + 1)});
    }

    List<Integer> shortestPathOnHoles(int src, int tgt, Map<Integer, List<Integer>> holeAdj) {
        final double INF = Double.POSITIVE_INFINITY;
        int n = holes.size();
        double[] dist = new double[n];
        int[] parent = new int[n];
        Arrays.fill(dist, INF);
        Arrays.fill(parent, -1);
        PriorityQueue<Pair> pq = new PriorityQueue<>();
        dist[src] = 0;
        pq.add(new Pair(src, 0));
        while (!pq.isEmpty()) {
            Pair p = pq.poll();
            int u = p.node;
            if (p.dist > dist[u]) continue;
            if (u == tgt) break;
            for (int v : holeAdj.get(u)) {
                double w = distance(holes.get(u).x, holes.get(u).y, holes.get(v).x, holes.get(v).y);
                if (dist[v] > dist[u] + w) {
                    dist[v] = dist[u] + w;
                    parent[v] = u;
                    pq.add(new Pair(v, dist[v]));
                }
            }
        }
        if (src == tgt) return Collections.singletonList(src);
        if (parent[tgt] == -1) return null;
        LinkedList<Integer> path = new LinkedList<>();
        int cur = tgt;
        path.addFirst(cur);
        while (cur != src) {
            cur = parent[cur];
            if (cur == -1) return null;
            path.addFirst(cur);
        }
        return path;
    }

    static double distance(int x1, int y1, int x2, int y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    static class Hex {
        final int index, cx, cy, r;
        int[] vertexHoleIndices;

        Hex(int index, int cx, int cy, int r) {
            this.index = index;
            this.cx = cx;
            this.cy = cy;
            this.r = r;
        }

        Point vertex(int v) {
            double angle = Math.toRadians(60 * v);
            return new Point(cx + (int) Math.round(r * Math.cos(angle)), cy + (int) Math.round(r * Math.sin(angle)));
        }
    }

    static class Hole {
        final int hexIndex, vertexIndex, x, y;

        Hole(int hexIndex, int vertexIndex, int x, int y) {
            this.hexIndex = hexIndex;
            this.vertexIndex = vertexIndex;
            this.x = x;
            this.y = y;
        }
    }

    static class MSEdge implements Comparable<MSEdge> {
        final int u, v;
        final double w;

        MSEdge(int u, int v, double w) {
            this.u = u;
            this.v = v;
            this.w = w;
        }

        public int compareTo(MSEdge o) {
            return Double.compare(this.w, o.w);
        }
    }

    static class Pair implements Comparable<Pair> {
        final int node;
        final double dist;

        Pair(int node, double dist) {
            this.node = node;
            this.dist = dist;
        }

        public int compareTo(Pair o) {
            return Double.compare(this.dist, o.dist);
        }
    }

    static class UnionFind {
        int[] p;

        UnionFind(int n) {
            p = new int[n];
            for (int i = 0; i < n; i++) p[i] = i;
        }

        int find(int x) {
            return p[x] == x ? x : (p[x] = find(p[x]));
        }

        void union(int a, int b) {
            p[find(a)] = find(b);
        }
    }

    /* ===================== DRAWING ===================== */

    class DrawPanel extends JPanel {
        final int holeRadius = 5;
        final int agentRadius = 10;

        DrawPanel() {
            setBackground(new Color(250, 251, 253));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Point p = e.getPoint();
                    int clickedHole = findHoleAtPoint(p.x, p.y);
                    if (clickedHole == -1) return;

                    // Only active network nodes can be stimuli
                    if (!blueAgents.contains(clickedHole)) return;

                    NodeState u = nodeStates[clickedHole];
                    if (!u.stimulus) {
                        u.stimulus = true;
                        u.stimulusId = nextStimulusId++;
                        u.awareFromStimuli.add(u.stimulusId);
                    } else {
                        int sid = u.stimulusId;
                        u.stimulus = false;
                        u.clearingStimuli.add(sid);
                    }
                    repaint();
                }
            });
        }

        int findHoleAtPoint(int x, int y) {
            int thresh = 14;
            for (int i = 0; i < holes.size(); i++) {
                Hole h = holes.get(i);
                if (distance(x, y, h.x, h.y) <= thresh) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Perimeter edges
            g.setColor(new Color(200, 210, 220, 80));
            g.setStroke(new BasicStroke(1f));
            for (Hex h : hexes) {
                int[] v = h.vertexHoleIndices;
                for (int i = 0; i < 6; i++) {
                    Hole a = holes.get(v[i]);
                    Hole b = holes.get(v[(i + 1) % 6]);
                    g.drawLine(a.x, a.y, b.x, b.y);
                }
            }

            // Realized edges
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(46, 125, 50, 220));
            for (int[] e : realizedEdges) {
                Hole a = holes.get(e[0]), b = holes.get(e[1]);
                g.drawLine(a.x, a.y, b.x, b.y);
            }

            // Draw States (Halos)
            for (int i = 0; i < holes.size(); i++) {
                if (!blueAgents.contains(i)) continue;
                Hole h = holes.get(i);
                NodeState ns = nodeStates[i];

                if (ns.stimulus) {
                    g.setColor(new Color(60, 170, 60, 160)); // Active Source Green
                    g.fillOval(h.x - 20, h.y - 20, 40, 40);
                } else if (!ns.clearingStimuli.isEmpty()) {
                    g.setColor(new Color(160, 130, 210, 160)); // Clearing Wave Purple
                    g.fillOval(h.x - 18, h.y - 18, 36, 36);
                } else if (ns.isAware()) {
                    g.setColor(new Color(255, 160, 160, 160)); // Aware Pink
                    g.fillOval(h.x - 18, h.y - 18, 36, 36);
                }
            }

            // Blue agents
            for (int bi : blueAgents) {
                Hole h = holes.get(bi);
                g.setColor(new Color(30, 136, 229, 200));
                g.fillOval(h.x - agentRadius - 4, h.y - agentRadius - 4, (agentRadius + 4) * 2, (agentRadius + 4) * 2);
                g.setColor(new Color(21, 101, 192));
                g.fillOval(h.x - agentRadius, h.y - agentRadius, agentRadius * 2, agentRadius * 2);
                g.setColor(Color.WHITE);
                g.fillOval(h.x - 4, h.y - 4, 8, 8);
            }

            // Tokens
            g.setColor(new Color(220, 20, 20));
            for (Token t : tokens) {
                Hole th = holes.get(t.posIndex);
                int ox = rnd.nextInt(9) - 4; // slight jitter to see clustered tokens
                int oy = rnd.nextInt(9) - 4;
                g.fillOval(th.x + ox - 3, th.y + oy - 3, 6, 6);
            }

            // Faint unassigned holes
            g.setColor(new Color(150, 160, 170, 80));
            for (Hole h : holes) {
                if (!blueAgents.contains(h.vertexIndex)) {
                    g.drawOval(h.x - holeRadius, h.y - holeRadius, holeRadius * 2, holeRadius * 2);
                }
            }
        }
    }

    /* color legend */
    class LegendPanel extends JPanel {
        LegendPanel() {
            setLayout(new FlowLayout());

            addLegend(new Color(150, 160, 170), "Inactive Node");
            addLegend(new Color(30, 136, 229), "Active Network Node");
            addLegend(new Color(60, 170, 60, 160), "Active Stimulus");
            addLegend(new Color(255, 160, 160, 160), "Aware");
            addLegend(new Color(160, 130, 210, 160), "All Clear Wave");
            addLegend(Color.RED, "Token");
        }

        void addLegend(Color c, String text) {
            JPanel box = new JPanel();
            box.setBackground(c);
            box.setPreferredSize(new Dimension(15, 15));

            add(box);
            add(new JLabel(text));
        }
    }
}