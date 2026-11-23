package com.retro.explorer;

/** Colour/shape definitions for the four looks: Windows XP, Vista, 7 and Snaptu. */
final class Theme {
    String id, name;
    int frame, frameW;
    int[] title; int titleText, titleShadow, titleH; boolean titleBold, lightTitle, accentLine;
    boolean winButtons; int[] minBtn, closeBtn; int winBorder;
    int[] nav; int navText; int[] navBtn;
    int[] cmd; int cmdText; int[] tb; int tbPressed, tbRadius;
    int addrBg, addrText, addrBorder, addrRadius, addrHint;
    int content, contentText, dimText;
    int[] side; int sideText, sideHeader, sideSel, sideSelBorder, sideSelText;
    int sel, selBorder, selText, itemRadius;
    int[] details; int detailsText; boolean detailsPane;
    int[] status; int statusText;
    int[] header; int headerText;
    int accent; boolean dark, softkeys, xpTasks;

    static final String[][] ALL = {{"xp", "Windows XP"}, {"vista", "Windows Vista"}, {"7", "Windows 7"}, {"snaptu", "Snaptu"}};

    static Theme get(String id) {
        if ("xp".equals(id)) return xp();
        if ("vista".equals(id)) return vista();
        if ("snaptu".equals(id)) return snaptu();
        return seven();
    }

    private static Theme xp() {
        Theme t = new Theme();
        t.id = "xp"; t.name = "Windows XP";
        t.frame = 0xFF0831D9; t.frameW = 3;
        t.title = new int[]{0xFF0997FF, 0xFF0053EE, 0xFF0050EE, 0xFF0066FF, 0xFF0066FF, 0xFF003DD7};
        t.titleText = 0xFFFFFFFF; t.titleShadow = 0xFF0F1089; t.titleH = 32; t.titleBold = true;
        t.winButtons = true; t.minBtn = new int[]{0xFF5C9BFB, 0xFF2E6BE8, 0xFF1C4FD0};
        t.closeBtn = new int[]{0xFFF0956F, 0xFFE2521F, 0xFFC73A12}; t.winBorder = 0xFFFFFFFF;
        t.nav = new int[]{0xFFECE9D8}; t.navText = 0xFF444444; t.navBtn = new int[]{0xFF6CCB5F, 0xFF3AA33A, 0xFF1F6E1F};
        t.cmd = new int[]{0xFFFDFDFB, 0xFFECE9D8}; t.cmdText = 0xFF000000; t.tbPressed = 0xFFE3E2DA; t.tbRadius = 3;
        t.addrBg = 0xFFFFFFFF; t.addrText = 0xFF000000; t.addrBorder = 0xFF7F9DB9; t.addrRadius = 0; t.addrHint = 0xFF888888;
        t.content = 0xFFFFFFFF; t.contentText = 0xFF000000; t.dimText = 0xFF555555;
        t.side = new int[]{0xFF7BA2E7, 0xFF6375D6}; t.sideText = 0xFFFFFFFF; t.sideHeader = 0xFFFFFFFF;
        t.sideSel = 0xFF316AC5; t.sideSelBorder = 0xFF316AC5; t.sideSelText = 0xFFFFFFFF;
        t.sel = 0xFF316AC5; t.selBorder = 0xFF316AC5; t.selText = 0xFFFFFFFF; t.itemRadius = 0;
        t.details = new int[]{0xFFFFFFFF}; t.detailsText = 0xFF000000; t.detailsPane = false;
        t.status = new int[]{0xFFECE9D8}; t.statusText = 0xFF000000;
        t.header = new int[]{0xFFFFFFFF, 0xFFEBEADB}; t.headerText = 0xFF000000;
        t.accent = 0xFF316AC5; t.xpTasks = true;
        return t;
    }

    private static Theme vista() {
        Theme t = new Theme();
        t.id = "vista"; t.name = "Windows Vista";
        t.frame = 0xFF1C2B3B; t.frameW = 4;
        t.title = new int[]{0xFF4A5D72, 0xFF22344A}; t.titleText = 0xFFFFFFFF; t.titleShadow = 0xFF000000; t.titleH = 30;
        t.winButtons = true; t.minBtn = new int[]{0xFF7A8DA1, 0xFF4E6176, 0xFF2A3B4E, 0xFF4B6075};
        t.closeBtn = new int[]{0xFFE9A99C, 0xFFD7614B, 0xFFB4250B, 0xFFD8762F}; t.winBorder = 0xFF000000;
        t.nav = new int[]{0xFF22344A, 0xFF1C2B3B}; t.navText = 0xFFFFFFFF; t.navBtn = new int[]{0xFF8FD3FF, 0xFF2B84D6, 0xFF14469E};
        t.cmd = new int[]{0xFF5D7087, 0xFF2A3D52, 0xFF0E1A28, 0xFF1A3045}; t.cmdText = 0xFFFFFFFF; t.tbPressed = 0x50FFFFFF; t.tbRadius = 3;
        t.addrBg = 0xFFF5F7FA; t.addrText = 0xFF000000; t.addrBorder = 0xFF000000; t.addrRadius = 2; t.addrHint = 0xFF6D6D6D;
        t.content = 0xFFFFFFFF; t.contentText = 0xFF000000; t.dimText = 0xFF6D6D6D;
        t.side = new int[]{0xFFFAFCFF, 0xFFE7EEF7}; t.sideText = 0xFF1E3287; t.sideHeader = 0xFF1E3287;
        t.sideSel = 0xFFCCE3FB; t.sideSelBorder = 0xFF7DA2CE; t.sideSelText = 0xFF000000;
        t.sel = 0xFFCCE3FB; t.selBorder = 0xFF7DA2CE; t.selText = 0xFF000000; t.itemRadius = 3;
        t.details = new int[]{0xFFEEF4FA, 0xFFD7E3EF}; t.detailsText = 0xFF1E2F45; t.detailsPane = true;
        t.status = new int[]{0xFFF2F5F9, 0xFFDDE6EF}; t.statusText = 0xFF1E2F45;
        t.header = new int[]{0xFFFFFFFF, 0xFFF1F3F6}; t.headerText = 0xFF4C607A;
        t.accent = 0xFF3399FF;
        return t;
    }

    private static Theme seven() {
        Theme t = new Theme();
        t.id = "7"; t.name = "Windows 7";
        t.frame = 0xFFB9D1EA; t.frameW = 4;
        t.title = new int[]{0xFFDCE8F5, 0xFFC4D8EE}; t.titleText = 0xFF000000; t.titleShadow = 0xFFFFFFFF; t.titleH = 30; t.lightTitle = true;
        t.winButtons = true; t.minBtn = new int[]{0xFFE3EDF8, 0xFFC9DBEF, 0xFFA7C1E0, 0xFFC5D9EF};
        t.closeBtn = new int[]{0xFFE8A493, 0xFFD36B55, 0xFFC23C26, 0xFFD6795F}; t.winBorder = 0xFF536B8A;
        t.nav = new int[]{0xFFC4D8EE, 0xFFB9D1EA}; t.navText = 0xFF1E395B; t.navBtn = new int[]{0xFF9FDCFF, 0xFF2E8AE0, 0xFF1555B0};
        t.cmd = new int[]{0xFFF5FAFF, 0xFFE6EEF8, 0xFFDCE6F4, 0xFFDDE9F7}; t.cmdText = 0xFF1E395B; t.tbPressed = 0xFFCDDCEF; t.tbRadius = 3;
        t.addrBg = 0xFFFFFFFF; t.addrText = 0xFF000000; t.addrBorder = 0xFF8EA4C1; t.addrRadius = 2; t.addrHint = 0xFF6D6D6D;
        t.content = 0xFFFFFFFF; t.contentText = 0xFF000000; t.dimText = 0xFF6D6D6D;
        t.side = new int[]{0xFFFFFFFF}; t.sideText = 0xFF000000; t.sideHeader = 0xFF1E395B;
        t.sideSel = 0xFFD9EEFC; t.sideSelBorder = 0xFF7AB8E4; t.sideSelText = 0xFF000000;
        t.sel = 0xFFCCE4FB; t.selBorder = 0xFF7DA2CE; t.selText = 0xFF000000; t.itemRadius = 3;
        t.details = new int[]{0xFFFDFEFF, 0xFFEEF3FA}; t.detailsText = 0xFF1E395B; t.detailsPane = true;
        t.status = new int[]{0xFFF0F0F0}; t.statusText = 0xFF1E395B;
        t.header = new int[]{0xFFFFFFFF}; t.headerText = 0xFF4C607A;
        t.accent = 0xFF3399FF;
        return t;
    }

    private static Theme snaptu() {
        Theme t = new Theme();
        t.id = "snaptu"; t.name = "Snaptu";
        t.frame = 0xFF141414; t.frameW = 0;
        t.title = new int[]{0xFF3D3D3D, 0xFF1A1A1A}; t.titleText = 0xFFFFFFFF; t.titleShadow = 0xFF000000; t.titleH = 48;
        t.titleBold = true; t.accentLine = true;
        t.winButtons = false;
        t.nav = new int[]{0xFF1C1C1C}; t.navText = 0xFFEEEEEE; t.navBtn = new int[]{0xFFFFB45C, 0xFFFF8A00, 0xFFE06A00};
        t.cmd = new int[]{0xFF171717}; t.cmdText = 0xFFEEEEEE; t.tb = new int[]{0xFF383838, 0xFF262626}; t.tbPressed = 0xFFFF8A00; t.tbRadius = 18;
        t.addrBg = 0xFF2B2B2B; t.addrText = 0xFFEEEEEE; t.addrBorder = 0xFF3A3A3A; t.addrRadius = 18; t.addrHint = 0xFF888888;
        t.content = 0xFF161616; t.contentText = 0xFFEEEEEE; t.dimText = 0xFF999999;
        t.side = new int[]{0xFF181818}; t.sideText = 0xFFDDDDDD; t.sideHeader = 0xFFFF8A00;
        t.sideSel = 0xFFFF8A00; t.sideSelBorder = 0xFFFF8A00; t.sideSelText = 0xFF000000;
        t.sel = 0x40FF8A00; t.selBorder = 0xFFFF8A00; t.selText = 0xFFFFFFFF; t.itemRadius = 12;
        t.details = new int[]{0xFF1A1A1A}; t.detailsText = 0xFFDDDDDD; t.detailsPane = false;
        t.status = new int[]{0xFF1A1A1A}; t.statusText = 0xFFAAAAAA;
        t.header = new int[]{0xFF1F1F1F}; t.headerText = 0xFFCCCCCC;
        t.accent = 0xFFFF8A00; t.dark = true; t.softkeys = true;
        return t;
    }
}
