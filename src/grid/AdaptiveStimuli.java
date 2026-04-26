package grid;
import javax.swing.*;
import javax.swing.Timer;



import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.util.*;

/* =========================================================
   Adaptive Stimuli Simulation
   Random Walk Probability = 1.00
   With GUI Legend
   ========================================================= */

public class AdaptiveStimuli {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimFrame(35, 35));
    }
}

/* ================= MODEL ================= */

class Cell {

    boolean stimulus = false;
    int stimulusId = -1;

    Set<Integer> awareFromStimuli = new HashSet<>();
    Set<Integer> clearingStimuli = new HashSet<>();

    boolean isAware() {
        return !awareFromStimuli.isEmpty();
    }
}

class Token {

    int r, c;
    int hopsLeft;
    int stimulusId;

    Token(int r, int c, int hops, int sid) {
        this.r = r;
        this.c = c;
        this.hopsLeft = hops;
        this.stimulusId = sid;
    }
}

/* ================= FRAME ================= */

class SimFrame extends JFrame {

    final int rows, cols;
    final Cell[][] grid;

    final java.util.List<Token> tokens = new ArrayList<>();

    final Random rnd = new Random();

    final GridPanel panel;

    Timer timer;

    /* ===== Simulation parameters ===== */

    double tokenGenProb = 0.15;

    double walkProb = 1.00;     // probability that token continues walking

    int tokenMaxHops = 12;

    int delayMs = 80;

    int nextStimulusId = 1;

    SimFrame(int r, int c) {

        super("Adaptive Stimuli Simulation");

        rows = r;
        cols = c;

        grid = new Cell[rows][cols];

        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                grid[i][j] = new Cell();

        panel = new GridPanel();

        JButton start = new JButton("Start / Pause");

        start.addActionListener(e -> toggle());

        JPanel top = new JPanel();
        top.add(start);

        add(top, BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        add(new LegendPanel(), BorderLayout.SOUTH);

        panel.addMouseListener(new MouseAdapter() {

            public void mouseClicked(MouseEvent e) {

                int x = e.getX() / panel.cell;
                int y = e.getY() / panel.cell;

                if (x >= 0 && x < cols && y >= 0 && y < rows)
                    handleClick(y, x);
            }
        });

        timer = new Timer(delayMs, e -> step());

        pack();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    void toggle() {

        if (timer.isRunning())
            timer.stop();
        else
            timer.start();
    }

    /* ================= INPUT ================= */

    void handleClick(int r, int c) {

        Cell u = grid[r][c];

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

    /* ================= SIMULATION ================= */

    void step() {

        /* TOKEN GENERATION */

        for (int i = 0; i < rows; i++) {

            for (int j = 0; j < cols; j++) {

                Cell u = grid[i][j];

                if (u.stimulus && rnd.nextDouble() < tokenGenProb) {

                    tokens.add(new Token(i, j, tokenMaxHops, u.stimulusId));
                }
            }
        }

        /* TOKEN WALK */

        java.util.List<Token> dead = new ArrayList<>();

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

            java.util.List<int[]> nbs = neighbors(t.r, t.c);

            Collections.shuffle(nbs, rnd);

            int[] nb = nbs.get(0);

            t.r = nb[0];
            t.c = nb[1];

            t.hopsLeft--;

            Cell v = grid[t.r][t.c];

            if (v.clearingStimuli.contains(t.stimulusId)) {

                dead.add(t);
                continue;
            }

            if (!v.awareFromStimuli.contains(t.stimulusId)) {

                v.awareFromStimuli.add(t.stimulusId);

                dead.add(t);
            }
        }

        tokens.removeAll(dead);

        /* CLEARING PROPAGATION */

        java.util.List<int[]> toProcess = new ArrayList<>();

        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                if (!grid[i][j].clearingStimuli.isEmpty())
                    toProcess.add(new int[]{i, j});

        for (int[] pos : toProcess) {

            int r0 = pos[0];
            int c0 = pos[1];

            Cell u = grid[r0][c0];

            Set<Integer> copy = new HashSet<>(u.clearingStimuli);

            for (int sid : copy) {

                for (int[] nb : neighbors(r0, c0)) {

                    Cell v = grid[nb[0]][nb[1]];

                    if (v.awareFromStimuli.contains(sid))
                        v.clearingStimuli.add(sid);

                    tokens.removeIf(t ->
                            t.r == nb[0] &&
                            t.c == nb[1] &&
                            t.stimulusId == sid);
                }

                u.awareFromStimuli.remove(sid);
                u.clearingStimuli.remove(sid);
            }
        }

        repaint();
    }

    /* ================= NEIGHBORS ================= */

    java.util.List<int[]> neighbors(int r, int c) {

        java.util.List<int[]> list = new ArrayList<>();

        for (int dr = -1; dr <= 1; dr++)
            for (int dc = -1; dc <= 1; dc++) {

                if (dr == 0 && dc == 0)
                    continue;

                int nr = r + dr;
                int nc = c + dc;

                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols)
                    list.add(new int[]{nr, nc});
            }

        return list;
    }

    /* ================= GRID DRAW ================= */

    class GridPanel extends JPanel {

        final int cell = 14;

        public Dimension getPreferredSize() {
            return new Dimension(cols * cell, rows * cell);
        }

        protected void paintComponent(Graphics g) {

            super.paintComponent(g);

            for (int i = 0; i < rows; i++) {

                for (int j = 0; j < cols; j++) {

                    Cell u = grid[i][j];

                    if (u.stimulus)
                        g.setColor(Color.GREEN);

                    else if (!u.clearingStimuli.isEmpty())
                        g.setColor(new Color(160,130,210));

                    else if (u.isAware())
                        g.setColor(new Color(255,160,160));

                    else
                        g.setColor(new Color(255,240,170));

                    g.fillRect(j * cell, i * cell, cell-1, cell-1);
                }
            }

            g.setColor(Color.RED);

            for (Token t : tokens) {

                g.fillOval(t.c * cell + 4, t.r * cell + 4, 6, 6);
            }
        }
    }

    /* ================= LEGEND ================= */

    class LegendPanel extends JPanel {

        LegendPanel() {

            setLayout(new FlowLayout());

            addLegend(Color.YELLOW, "Active Agent");
            addLegend(Color.GREEN, "Witness");
            addLegend(new Color(255,160,160), "Aware");
            addLegend(Color.RED, "Token");
            addLegend(new Color(160,130,210), "All Clear");
        }

        void addLegend(Color c, String text) {

            JPanel box = new JPanel();
            box.setBackground(c);
            box.setPreferredSize(new Dimension(15,15));

            add(box);
            add(new JLabel(text));
        }
    }
}