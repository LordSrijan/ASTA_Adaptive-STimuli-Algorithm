package grid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
/*
 * PHASE 0:
 * Asynchronous Agent Framework Prototype
 * Goal:
 * - Agent mailboxes
 * - Thread pool
 * - Independent wake/sleep
 * - Message passing
 * - Swing repaint only
 */

public class AsyncPrototype {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrototypeFrame(10, 10));
    }
}

/* ================= MESSAGES ================= */

abstract class Message {}

class StimulusAddMessage extends Message {

    int stimulusId;

    StimulusAddMessage(int stimulusId) {

        this.stimulusId = stimulusId;
    }
}

class StimulusRemoveMessage extends Message {}
class ClearMessage extends Message {

    int stimulusId;

    ClearMessage(int stimulusId) {

        this.stimulusId = stimulusId;
    }
}
class ShadowClearMessage extends Message {

    int stimulusId;

    ShadowClearMessage(int stimulusId) {

        this.stimulusId = stimulusId;
    }
}
/* ================= AGENT ================= */

class CellAgent {
    static final double TOKEN_GEN_PROB = 1.00;
    static final double WALK_PROB = 1.00;
    static final int TOKEN_MAX_HOPS = 12;
    final int row;
    final int col;
    PrototypeFrame frame;
    Set<Integer> awareFromStimuli =
            ConcurrentHashMap.newKeySet();

    Set<Integer> clearingStimuli =
            ConcurrentHashMap.newKeySet();

    Set<Integer> shadowClearedStimuli =
            ConcurrentHashMap.newKeySet();

    Set<Integer> clearProcessedStimuli =
            ConcurrentHashMap.newKeySet();
    volatile boolean stimulus = false;
    volatile int stimulusId = -1;
    CellAgent[][] grid;
    int rows;
    int cols;
    final BlockingQueue<Message> inbox =
            new LinkedBlockingQueue<>();
    CopyOnWriteArrayList<TokenView> tokenViews;
    final ScheduledExecutorService scheduler;

    final Random rnd = new Random();

    final AtomicBoolean scheduled =
            new AtomicBoolean(false);
    void setFrame(PrototypeFrame frame) {
        this.frame = frame;
    }
    void setTokenViews(
            CopyOnWriteArrayList<TokenView> tokenViews) {

        this.tokenViews = tokenViews;
    }
    void initGrid(CellAgent[][] grid, int rows, int cols) {
        this.grid = grid;
        this.rows = rows;
        this.cols = cols;
    }

    java.util.List<CellAgent> neighbors() {

        java.util.List<CellAgent> list =
                new java.util.ArrayList<>();

        for (int dr = -1; dr <= 1; dr++) {

            for (int dc = -1; dc <= 1; dc++) {

                if (dr == 0 && dc == 0)
                    continue;

                int nr = row + dr;
                int nc = col + dc;

                if (nr >= 0 &&
                        nr < rows &&
                        nc >= 0 &&
                        nc < cols) {

                    list.add(grid[nr][nc]);
                }

            }
        }

        return list;
    }

    CellAgent(int r, int c,
              ScheduledExecutorService scheduler) {

        this.row = r;
        this.col = c;
        this.scheduler = scheduler;
    }

    void post(Message msg) {

        inbox.offer(msg);

        scheduleIfNeeded();
    }

    void scheduleIfNeeded() {
            
        if (scheduled.compareAndSet(false, true)) {

            long delay =
                    60 + rnd.nextInt(40);

            scheduler.schedule(
                    this::runAgent,
                    delay,
                    TimeUnit.MILLISECONDS);
        }
    }

    void runAgent() {
        while (!frame.running) {

    try {

        Thread.sleep(50);

    } catch (InterruptedException e) {

        Thread.currentThread().interrupt();
        return;
    }
}
        try {

            Message msg;

            while ((msg = inbox.poll()) != null) {

                process(msg);
            }

        } finally {

            scheduled.set(false);

            if (!inbox.isEmpty()) {
                scheduleIfNeeded();
            }

            if (stimulus) {

                if (rnd.nextDouble() < TOKEN_GEN_PROB) {

                    tokenViews.add(
                            new TokenView(
                                    row,
                                    col,
                                    System.currentTimeMillis() + 250));

                    inbox.offer(
                            new TokenMessage(stimulusId,
                                    TOKEN_MAX_HOPS));
                }

                scheduleIfNeeded();
            }
        }
    }

    void process(Message msg) {

        if (msg instanceof StimulusAddMessage) {

            stimulus = true;

            stimulusId =
                    ((StimulusAddMessage)msg).stimulusId;

            awareFromStimuli.add(
                    stimulusId);

            shadowClearedStimuli.remove(
                    stimulusId);

            clearProcessedStimuli.remove(
                    stimulusId);
            System.out.println(
                    "Stimulus ON at ("
                            + row + "," + col + ")");

        } else if (msg instanceof StimulusRemoveMessage) {
           

            if (!clearProcessedStimuli.contains(
                    stimulusId)) {

                clearingStimuli.add(
                        stimulusId);

                post(
                        new ClearMessage(
                                stimulusId));
            }

            System.out.println(
                    "Stimulus OFF at ("
                            + row + "," + col + ")");
        }
        else if (msg instanceof ClearMessage clear) {

            if (clearProcessedStimuli.contains(
                    clear.stimulusId))
                return;

            clearProcessedStimuli.add(
                    clear.stimulusId);

            clearingStimuli.add(
                    clear.stimulusId);

            System.out.println(
                    "CLEAR at (" +
                            row + "," +
                            col + ")");

            for (CellAgent nb : neighbors()) {

                if (nb.awareFromStimuli.contains(
                        clear.stimulusId)) {

                    nb.post(new ClearMessage(clear.stimulusId));
                }
                else {

                    nb.post(new ShadowClearMessage(clear.stimulusId));
                }
            }

            scheduler.schedule(
                    () -> {

                        awareFromStimuli.remove(
                                clear.stimulusId);

                        clearingStimuli.remove(
                                clear.stimulusId);

                        clearProcessedStimuli.remove(
                                clear.stimulusId);

                        shadowClearedStimuli.add(
                                clear.stimulusId);
                        if (stimulus &&
                                stimulusId ==
                                        clear.stimulusId) {

                            stimulus = false;
                            stimulusId = -1;
                        }
                    },
                    250,
                    TimeUnit.MILLISECONDS);
        }
        else if (msg instanceof ShadowClearMessage shadow) {

            shadowClearedStimuli.add(
                    shadow.stimulusId);
            System.out.println(
                    "SHADOW at (" +
                            row + "," +
                            col + ")");
            inbox.removeIf(m ->

                    m instanceof TokenMessage token

                            &&

                            token.stimulusId ==
                                    shadow.stimulusId);
            if (awareFromStimuli.contains(
                    shadow.stimulusId)
                    && !stimulus) {

                awareFromStimuli.remove(
                        shadow.stimulusId);
            }
        }
        else if (msg instanceof TokenMessage token) {
            if (clearingStimuli.contains(
                    token.stimulusId)
                    ||
                    shadowClearedStimuli.contains(
                            token.stimulusId))
                return;
            if (token.hopsLeft <= 0)
                return;

            if (!awareFromStimuli.contains(
                    token.stimulusId)) {

                awareFromStimuli.add(
                        token.stimulusId);

                System.out.println(
                        "Aware at (" +
                                row + "," +
                                col + ")");

                return;
            }

            if (rnd.nextDouble() > WALK_PROB)
                return;

            java.util.List<CellAgent> nbs =
                    neighbors();

            if (nbs.isEmpty())
                return;

            CellAgent next =
                    nbs.get(
                            rnd.nextInt(
                                    nbs.size()));

            tokenViews.add(
                    new TokenView(
                            next.row,
                            next.col,
                            System.currentTimeMillis() + 250));
            next.post(
                    new TokenMessage(token.stimulusId,
                            token.hopsLeft - 1));
        }
    }
}
class TokenMessage extends Message {

    int stimulusId;

    int hopsLeft;

    TokenMessage(
            int stimulusId,
            int hopsLeft) {

        this.stimulusId = stimulusId;
        this.hopsLeft = hopsLeft;
    }
}

class TokenView {

    volatile int row;
    volatile int col;

    volatile long expiry;

    TokenView(int row, int col, long expiry) {

        this.row = row;
        this.col = col;
        this.expiry = expiry;
    }
}
/* ================= FRAME ================= */

class PrototypeFrame extends JFrame {
    final AtomicInteger nextStimulusId =
            new AtomicInteger(1);
    final int rows;
    final int cols;
    volatile boolean running = false;
    final CellAgent[][] agents;
    final CopyOnWriteArrayList<TokenView> tokenViews =
            new CopyOnWriteArrayList<>();
    final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(
                   30);

    final GridPanel panel;

    PrototypeFrame(int rows, int cols) {

        super("Async Prototype");

        this.rows = rows;
        this.cols = cols;

        agents = new CellAgent[rows][cols];

        for (int r = 0; r < rows; r++) {

            for (int c = 0; c < cols; c++) {

                agents[r][c] =
                        new CellAgent(
                                r,
                                c,
                                scheduler);
            }
        }

        for (int r = 0; r < rows; r++) {

            for (int c = 0; c < cols; c++) {

                agents[r][c].initGrid(
                        agents,
                        rows,
                        cols);

            }
        }
        for (int r = 0; r < rows; r++) {

            for (int c = 0; c < cols; c++) {

                agents[r][c].setFrame(this);

            }
        }
        for (int r = 0; r < rows; r++) {

            for (int c = 0; c < cols; c++) {

                agents[r][c].setTokenViews(
                        tokenViews);
            }
        }

       panel = new GridPanel();

JButton startPause =
        new JButton("Start");

startPause.addActionListener(e -> {

    running = !running;

    if (running) {

        startPause.setText("Pause");
         for (int r = 0; r < rows; r++) {

        for (int c = 0; c < cols; c++) {

            CellAgent a = agents[r][c];

            if (a.stimulus ||
                    !a.inbox.isEmpty()) {

                a.scheduleIfNeeded();
            }
        }
    }

    } else {

        startPause.setText("Start");
    }
});
 class LegendPanel extends JPanel {
        
        LegendPanel() {
            setLayout(
        new BorderLayout());
        setLayout(new FlowLayout());

        addLegend(
                Color.CYAN,
                "Unaware");

        addLegend(
                new Color(255,160,160),
                "Aware");

        addLegend(
                Color.GREEN,
                "Witness");

        addLegend(
                new Color(160,130,210),
                "Clearing Wave");

        addLegend(
                Color.RED,
                "Token");
    }

    void addLegend(
            Color color,
            String text) {

        JPanel box =
                new JPanel();

        box.setBackground(color);

        box.setPreferredSize(
                new Dimension(15,15));

        add(box);

        add(new JLabel(text));
    }
}

JPanel top = new JPanel();

top.add(startPause);

add(top, BorderLayout.NORTH);

add(panel, BorderLayout.CENTER);
add(
        new LegendPanel(),
        BorderLayout.SOUTH);
        panel.addMouseListener(
                new MouseAdapter() {

                    @Override
                    public void mouseClicked(
                            MouseEvent e) {

                        int cell = panel.cell;

                        int c = e.getX() / cell;
                        int r = e.getY() / cell;

                        if (r < 0 || r >= rows ||
                                c < 0 || c >= cols)
                            return;

                        CellAgent a =
                                agents[r][c];
                        
                           if (!running) {

    if (!a.stimulus &&
            a.stimulusId == -1) {

        int id =
                nextStimulusId.getAndIncrement();

        a.stimulus = true;
        a.stimulusId = id;

        a.awareFromStimuli.add(id);

    } else if (a.stimulus) {
        a.stimulus = false;
        a.clearingStimuli.add(
                a.stimulusId);
            a.post(
            new ClearMessage(
                    a.stimulusId));
    }

    panel.repaint();

    return;
}     
                        
                        if (!a.stimulus) {

                            a.post(
                                    new StimulusAddMessage(
                                            nextStimulusId.getAndIncrement()));

                        } else {

                            a.post(
                                    new StimulusRemoveMessage());
                        }
                    }
                });
                
        
               

        /*
         * GUI refresh only.
         * No simulation logic.
         */
        new Timer(
                50,
                e -> {

                    long now =
                            System.currentTimeMillis();
                        if (running) {

                             tokenViews.removeIf(
                                 t -> t.expiry < now);
                        }


                    panel.repaint();
                })
                .start();

        pack();

        setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE);

        setLocationRelativeTo(null);

        setVisible(true);
    }

    /* ================= PANEL ================= */

    class GridPanel extends JPanel {

        final int cell = 40;

        @Override
        public Dimension getPreferredSize() {

            return new Dimension(
                    cols * cell,
                    rows * cell);
        }

        @Override
        protected void paintComponent(
                Graphics g) {

            super.paintComponent(g);

            for (int r = 0; r < rows; r++) {

                for (int c = 0; c < cols; c++) {

                    CellAgent a =
                            agents[r][c];

                    if (a.stimulus)
                        g.setColor(Color.GREEN);
                    else if (!a.clearingStimuli.isEmpty())
                        g.setColor(new Color(160,130,210));


                    else if (!a.awareFromStimuli.isEmpty())
                        g.setColor(new Color(255,160,160));

                    else
                        g.setColor(Color.CYAN);

                    g.fillRect(
                            c * cell,
                            r * cell,
                            cell - 1,
                            cell - 1);
                    g.setColor(new Color(50,50,50));

g.drawRect(
        c * cell,
        r * cell,
        cell - 1,
        cell - 1);

                }
            }
            g.setColor(Color.RED);

            for (TokenView t : tokenViews) {

                g.fillOval(
                        t.col * cell + cell / 3,
                        t.row * cell + cell / 3,
                        cell / 3,
                        cell / 3);
            }

        }
    }
}