import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import satori from 'satori';
import { Resvg } from '@resvg/resvg-js';
import fs from 'fs';

let cachedVersion = '14.5.1';
let cachedProfileIcons: string[] = [];

async function getDDragonVersion() {
    try {
        const res = await fetch('https://ddragon.leagueoflegends.com/api/versions.json');
        const versions = await res.json();
        cachedVersion = versions[0];
    } catch (e) {
        console.error("Failed to fetch DDragon versions");
    }
    return cachedVersion;
}

async function getProfileIconList(version: string) {
    if (cachedProfileIcons.length > 0) return cachedProfileIcons;
    try {
        const rs = await fetch(`https://ddragon.leagueoflegends.com/cdn/${version}/data/en_US/profileicon.json`);
        const data = await rs.json();
        cachedProfileIcons = Object.keys(data.data);
    } catch (e) {
        cachedProfileIcons = ['1', '2', '3', '4', '5', '6', '7', '8', '9'];
    }
    return cachedProfileIcons;
}

function hashCode(str: string) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        hash = str.charCodeAt(i) + ((hash << 5) - hash);
    }
    return Math.abs(hash);
}

async function getPlayerData(nickname: string) {
    const version = await getDDragonVersion();
    const icons = await getProfileIconList(version);
    
    const hash = hashCode(nickname);
    const iconId = icons[hash % icons.length];
    
    const ranks = ['iron', 'bronze', 'silver', 'gold', 'platinum', 'emerald', 'diamond', 'master', 'grandmaster', 'challenger'];
    const rankColors: Record<string, string> = {
        iron: '#a29596', bronze: '#b06d4e', silver: '#a6b3c2', gold: '#dbb443',
        platinum: '#1a9a8d', emerald: '#29b85c', diamond: '#5e43c5', master: '#c94cb1',
        grandmaster: '#d13838', challenger: '#5bc0eb'
    };
    
    const rankIndex = hash % ranks.length;
    const rankName = ranks[rankIndex];
    const color = rankColors[rankName];
    
    const level = (hash % 400) + 30;
    
    const matches = (hash % 1000) + 50;
    const winRateBase = 0.40 + ((hash % 200) / 1000);
    const wins = Math.floor(matches * winRateBase);
    const losses = matches - wins;
    const winRate = ((wins / matches) * 100).toFixed(1) + '%';
    
    const lp = rankName === 'master' || rankName === 'grandmaster' || rankName === 'challenger'
        ? (hash % 1500)
        : (hash % 100);
    const division = ['I', 'II', 'III', 'IV'][hash % 4];
    
    const rankFullName = rankName.charAt(0).toUpperCase() + rankName.slice(1) + (
        ['master', 'grandmaster', 'challenger'].includes(rankName) ? '' : ` ${division}`
    );
    
    return {
        name: nickname,
        level,
        rankName: rankFullName,
        lp,
        matches,
        wins,
        losses,
        winRate,
        color,
        profileIconUrl: `https://ddragon.leagueoflegends.com/cdn/${version}/img/profileicon/${iconId}.png`,
        rankIconUrl: `https://opgg-static.akamaized.net/images/medals_new/${rankName}.png`,
    };
}

async function getFontData() {
    const fontPath = path.join(process.cwd(), 'font.ttf');
    if (fs.existsSync(fontPath)) {
        return fs.readFileSync(fontPath);
    }
    
    let ttfUrl = 'https://fonts.gstatic.com/s/inter/v20/UcCO3FwrK3iLTeHuS_nVMrMxCp50SjIw2boKoduKmMEVuLyfAZ9hjZ-Ck-8.ttf';
    try {
        const cssRes = await fetch("https://fonts.googleapis.com/css?family=Inter", {
            headers: {
                "User-Agent": "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30"
            }
        });
        const css = await cssRes.text();
        const match = css.match(/url\((https:\/\/[^)]+\.ttf)\)/);
        if (match) {
            ttfUrl = match[1];
        }
    } catch (e) {
        console.error('Failed to fetch Google Fonts CSS, using fallback font url.', e);
    }

    const response = await fetch(ttfUrl);
    if (!response.ok) {
        throw new Error('Failed to download font TTF: ' + response.statusText);
    }
    const arrayBuffer = await response.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);
    fs.writeFileSync(fontPath, buffer);
    return buffer;
}

async function startServer() {
  const app = express();
  const PORT = 3000;
  
  app.use(express.json());

  // API routes FIRST
  app.get("/api/health", (req, res) => {
    res.json({ status: "ok" });
  });

  app.post('/api/generate', async (req, res) => {
    try {
        const { nickname } = req.body;
        const data = await getPlayerData(nickname || 'Unknown');
        const fontData = await getFontData();

        const svg = await satori(
            {
                type: 'div',
                props: {
                    style: {
                        display: 'flex', flexDirection: 'column',
                        width: '400px', height: '600px',
                        backgroundColor: '#0f172a',
                        color: 'white', fontFamily: 'Inter',
                        borderRadius: '24px', overflow: 'hidden',
                        border: `6px solid ${data.color}`,
                        position: 'relative'
                    },
                    children: [
                        {
                            type: 'div',
                            props: {
                                style: {
                                    display: 'flex', flexDirection: 'column', alignItems: 'center',
                                    padding: '40px 20px 20px',
                                },
                                children: [
                                    {
                                        type: 'div',
                                        props: {
                                            style: {
                                                display: 'flex', width: '120px', height: '120px',
                                                borderRadius: '50%',
                                                border: `4px solid ${data.color}`,
                                                overflow: 'hidden',
                                                position: 'relative',
                                                backgroundColor: '#1e293b'
                                            },
                                            children: [
                                                { type: 'img', props: { src: data.profileIconUrl, style: { width: '100%', height: '100%', objectFit: 'cover' } } }
                                            ]
                                        }
                                    },
                                    {
                                        type: 'h1',
                                        props: {
                                            style: { fontSize: '32px', fontWeight: 800, marginTop: '20px', marginBottom: '8px', textAlign: 'center' },
                                            children: data.name
                                        }
                                    },
                                    {
                                        type: 'span',
                                        props: {
                                            style: { 
                                                background: data.color, padding: '4px 16px', borderRadius: '16px',
                                                fontSize: '16px', fontWeight: 600, color: '#fff' 
                                            },
                                            children: `Level ${data.level}`
                                        }
                                    }
                                ]
                            }
                        },
                        {
                            type: 'div',
                            props: {
                                style: {
                                    display: 'flex', flexDirection: 'column', alignItems: 'center',
                                    flex: 1, padding: '10px 30px'
                                },
                                children: [
                                    {
                                        type: 'div',
                                        props: {
                                            style: { display: 'flex', alignItems: 'center', marginBottom: '24px' },
                                            children: [
                                                { type: 'img', props: { src: data.rankIconUrl, style: { width: '90px', height: '90px', marginRight: '16px' } } },
                                                { 
                                                    type: 'div', 
                                                    props: { 
                                                        style: { display: 'flex', flexDirection: 'column' },
                                                        children: [
                                                            { type: 'span', props: { style: { fontSize: '28px', fontWeight: 800, color: data.color, marginBottom: '2px' }, children: data.rankName } },
                                                            { type: 'span', props: { style: { fontSize: '18px', color: '#94a3b8', fontWeight: 500 }, children: `${data.lp} LP` } }
                                                        ] 
                                                    } 
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        type: 'div',
                                        props: {
                                            style: {
                                                display: 'flex', width: '100%', justifyContent: 'space-between',
                                                backgroundColor: 'rgba(255,255,255,0.05)', borderRadius: '16px', padding: '16px',
                                                border: '1px solid rgba(255,255,255,0.1)'
                                            },
                                            children: [
                                                {
                                                    type: 'div',
                                                    props: {
                                                        style: { display: 'flex', flexDirection: 'column', alignItems: 'center' },
                                                        children: [
                                                            { type: 'span', props: { style: { color: '#94a3b8', fontSize: '14px', marginBottom: '4px' }, children: 'Win Rate' }},
                                                            { type: 'span', props: { style: { fontSize: '20px', fontWeight: 700, color: '#fff' }, children: data.winRate }}
                                                        ]
                                                    }
                                                },
                                                {
                                                    type: 'div',
                                                    props: {
                                                        style: { display: 'flex', flexDirection: 'column', alignItems: 'center' },
                                                        children: [
                                                            { type: 'span', props: { style: { color: '#94a3b8', fontSize: '14px', marginBottom: '4px' }, children: 'Games' }},
                                                            { type: 'span', props: { style: { fontSize: '20px', fontWeight: 700, color: '#fff' }, children: `${data.matches}` }}
                                                        ]
                                                    }
                                                },
                                                {
                                                    type: 'div',
                                                    props: {
                                                        style: { display: 'flex', flexDirection: 'column', alignItems: 'center' },
                                                        children: [
                                                            { type: 'span', props: { style: { color: '#94a3b8', fontSize: '14px', marginBottom: '4px' }, children: 'W / L' }},
                                                            { type: 'span', props: { style: { fontSize: '20px', fontWeight: 700, color: '#fff' }, children: `${data.wins} / ${data.losses}` }}
                                                        ]
                                                    }
                                                }
                                            ]
                                        }
                                    }
                                ]
                            }
                        },
                        {
                            type: 'div',
                            props: {
                                style: { display: 'flex', justifyContent: 'center', padding: '16px', borderTop: '1px solid rgba(255,255,255,0.1)', marginTop: 'auto' },
                                children: [
                                    { type: 'span', props: { style: { fontSize: '14px', color: '#64748b' }, children: 'Nexus-Sight.pl' } }
                                ]
                            }
                        }
                    ],
                },
            },
            { 
              width: 400, 
              height: 600, 
              fonts: [{ name: 'Inter', data: fontData, weight: 400, style: 'normal' }] 
            }
        );

        const resvg = new Resvg(svg);
        const pngBuffer = resvg.render().asPng();
        res.set('Content-Type', 'image/png').send(pngBuffer);
    } catch (error) {
        console.error(error);
        res.status(500).send('Error generating card');
    }
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
