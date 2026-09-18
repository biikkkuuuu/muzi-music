/* -------------------------------------------------------------
   MUZI MUSIC — AARDVARK STYLE 3D THREE.JS & LENIS LOGIC
   ------------------------------------------------------------- */

window.addEventListener('DOMContentLoaded', () => {
    initLenisScroll();
    initHero3D();
    initPlayerEngine();
    initHeaderScroll();
});

// -------------------------------------------------------------
// 1. LENIS SMOOTH SCROLLING
// -------------------------------------------------------------
function initLenisScroll() {
    if (typeof Lenis !== 'undefined') {
        const lenis = new Lenis({
            duration: 1.2,
            easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
            smoothWheel: true
        });

        function raf(time) {
            lenis.raf(time);
            requestAnimationFrame(raf);
        }
        requestAnimationFrame(raf);
    }
}

// -------------------------------------------------------------
// 2. THREE.JS 3D HERO VINYL RECORD
// -------------------------------------------------------------
let scene, camera, renderer;
let vinylMesh, particles;
let isPlaying3D = false;

function initHero3D() {
    const container = document.getElementById('hero-3d-canvas');
    if (!container) return;

    scene = new THREE.Scene();

    camera = new THREE.PerspectiveCamera(45, container.clientWidth / container.clientHeight, 0.1, 1000);
    camera.position.set(0, 0, 10);

    renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
    renderer.setSize(container.clientWidth, container.clientHeight);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(renderer.domElement);

    // Lights
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.9);
    scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0xffffff, 1.5);
    dirLight.position.set(5, 5, 5);
    scene.add(dirLight);

    // --- CREATE 3D VINYL RECORD ---
    const group = new THREE.Group();

    // Base Disc
    const discGeo = new THREE.CylinderGeometry(3.2, 3.2, 0.08, 64);
    const discMat = new THREE.MeshStandardMaterial({
        color: 0x0a0a0d,
        metalness: 0.95,
        roughness: 0.15
    });
    const disc = new THREE.Mesh(discGeo, discMat);
    disc.rotation.x = Math.PI / 2;
    group.add(disc);

    // Concentric Grooves
    for (let r = 1.1; r < 3.1; r += 0.14) {
        const ringGeo = new THREE.RingGeometry(r, r + 0.015, 64);
        const ringMat = new THREE.MeshBasicMaterial({ color: 0x1f1f26, side: THREE.DoubleSide });
        const ring = new THREE.Mesh(ringGeo, ringMat);
        ring.position.z = 0.05;
        group.add(ring);
    }

    // Yellow Center Label
    const labelGeo = new THREE.CircleGeometry(1.0, 64);
    const labelMat = new THREE.MeshStandardMaterial({
        color: 0xF9EA57,
        metalness: 0.3,
        roughness: 0.4
    });
    const label = new THREE.Mesh(labelGeo, labelMat);
    label.position.z = 0.055;
    group.add(label);

    // Center Spindle Hole
    const holeGeo = new THREE.CircleGeometry(0.16, 32);
    const holeMat = new THREE.MeshBasicMaterial({ color: 0x000000 });
    const hole = new THREE.Mesh(holeGeo, holeMat);
    hole.position.z = 0.06;
    group.add(hole);

    group.position.set(2.4, 0.2, 0);
    group.rotation.y = -0.35;
    group.rotation.x = 0.25;
    scene.add(group);
    vinylMesh = group;

    // --- PARTICLES ---
    const pCount = 300;
    const pPos = new Float32Array(pCount * 3);
    for (let i = 0; i < pCount * 3; i += 3) {
        pPos[i] = (Math.random() - 0.5) * 20;
        pPos[i + 1] = (Math.random() - 0.5) * 20;
        pPos[i + 2] = (Math.random() - 0.5) * 10;
    }
    const pGeo = new THREE.BufferGeometry();
    pGeo.setAttribute('position', new THREE.BufferAttribute(pPos, 3));
    const pMat = new THREE.PointsMaterial({ size: 0.07, color: 0x32225f, transparent: true, opacity: 0.5 });
    particles = new THREE.Points(pGeo, pMat);
    scene.add(particles);

    // Mouse Parallax Physics
    window.addEventListener('mousemove', (e) => {
        const mouseX = (e.clientX / window.innerWidth - 0.5) * 2;
        const mouseY = (e.clientY / window.innerHeight - 0.5) * 2;

        if (vinylMesh) {
            gsap.to(vinylMesh.rotation, {
                y: -0.35 + mouseX * 0.2,
                x: 0.25 + mouseY * 0.15,
                duration: 1.2,
                ease: "power2.out"
            });
        }
    });

    window.addEventListener('resize', () => {
        if (!container) return;
        camera.aspect = container.clientWidth / container.clientHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(container.clientWidth, container.clientHeight);
    });

    animate3D();
}

function animate3D() {
    requestAnimationFrame(animate3D);

    if (vinylMesh) {
        const speed = isPlaying3D ? 0.02 : 0.004;
        vinylMesh.children[0].rotation.z -= speed;
    }

    if (particles) {
        particles.rotation.y += 0.0006;
    }

    renderer.render(scene, camera);
}

// -------------------------------------------------------------
// 3. WAVY PLAYER & CARDS PLAYER INTERACTION
// -------------------------------------------------------------
function initPlayerEngine() {
    const audio = document.getElementById('demo-audio-player');
    const heroPlayBtn = document.getElementById('hero-play-btn');
    const pPlayToggle = document.getElementById('p-play-toggle');
    const heroIconState = document.getElementById('hero-icon-state');
    const pPlayIcon = document.getElementById('p-play-icon');

    const thumbDot = document.getElementById('wavy-thumb-dot');
    const pathActive = document.getElementById('wavy-active-path');
    const pathInactive = document.getElementById('wavy-inactive-path');
    const trackBox = document.getElementById('wavy-track-click');

    const songs = [
        {
            title: 'Barbaad (Movie: Saiyaara)',
            artist: 'Jubin Nautiyal',
            cover: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=600&auto=format&fit=crop',
            lyrics: [
                "तुझ से दूर मैं एक ही वजह के लिए हूँ",
                "तुझे छू लूँ तो कुछ मुझे हो जाएगा",
                "जो मैं चाहता ना हो मुझ को"
            ],
            duration: 224,
            src: 'https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3?filename=lofi-study-112191.mp3'
        },
        {
            title: 'Pardesiya (From "Param Sundari")',
            artist: 'Ali Saha, Amitabh Bhattacharya',
            cover: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600&auto=format&fit=crop',
            lyrics: [
                "परदेसिया यह सच है पिया",
                "सब कहते हैं मैंने तुझको दिल दे दिया",
                "मैं कहती हूँ तूने मेरा दिल ले लिया"
            ],
            duration: 231,
            src: 'https://cdn.pixabay.com/download/audio/2022/01/18/audio_d0a13f69d2.mp3?filename=chill-abstract-intention-12099.mp3'
        },
        {
            title: 'Midnight Lo-Fi Chill',
            artist: 'Muzi Beats Studio',
            cover: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop',
            lyrics: [
                "Late night coding and chill vibes",
                "Lost in the wave of sound",
                "Ad-free streaming all night"
            ],
            duration: 180,
            src: 'https://cdn.pixabay.com/download/audio/2022/03/15/audio_c8c8a14781.mp3?filename=ambient-piano-amp-strings-10711.mp3'
        }
    ];

    let songIdx = 0;
    let isPlaying = false;
    let progress = 0.42;
    let wavePhase = 0;

    audio.src = songs[songIdx].src;

    function togglePlay() {
        isPlaying = !isPlaying;
        isPlaying3D = isPlaying;

        if (isPlaying) {
            audio.play().catch(() => {});
            if (heroIconState) heroIconState.setAttribute('data-lucide', 'pause');
            if (pPlayIcon) pPlayIcon.setAttribute('data-lucide', 'pause');
        } else {
            audio.pause();
            if (heroIconState) heroIconState.setAttribute('data-lucide', 'play');
            if (pPlayIcon) pPlayIcon.setAttribute('data-lucide', 'play');
        }
        lucide.createIcons();
    }

    if (heroPlayBtn) heroPlayBtn.addEventListener('click', togglePlay);
    if (pPlayToggle) pPlayToggle.addEventListener('click', togglePlay);

    // Render Animated Wavy Path
    function updateWavyCanvas(p) {
        const width = 500;
        const activeX = width * p;
        const amp = isPlaying ? 6 : 2;
        const wavelength = 28;

        let activeD = `M 0 15`;
        for (let x = 0; x <= activeX; x += 5) {
            const y = 15 + Math.sin((x / wavelength) + wavePhase) * amp;
            activeD += ` L ${x.toFixed(1)} ${y.toFixed(1)}`;
        }

        let inactiveD = `M ${activeX.toFixed(1)} 15 L ${width} 15`;

        if (pathActive) pathActive.setAttribute('d', activeD);
        if (pathInactive) pathInactive.setAttribute('d', inactiveD);
        if (thumbDot) thumbDot.style.left = `${(p * 100).toFixed(1)}%`;
    }

    function renderWavyLoop() {
        if (isPlaying) {
            wavePhase += 0.12;
            if (audio.duration) {
                progress = audio.currentTime / audio.duration;
                updateTimestamps(audio.currentTime, audio.duration);
            }
        }
        updateWavyCanvas(progress);
        requestAnimationFrame(renderWavyLoop);
    }
    renderWavyLoop();

    function updateTimestamps(curr, total) {
        const currEl = document.getElementById('time-current');
        const totalEl = document.getElementById('time-total');
        if (currEl) currEl.textContent = formatTime(curr);
        if (totalEl) totalEl.textContent = formatTime(total);
    }

    function formatTime(s) {
        const m = Math.floor(s / 60);
        const sec = Math.floor(s % 60);
        return `${m}:${sec < 10 ? '0' : ''}${sec}`;
    }

    if (trackBox) {
        trackBox.addEventListener('click', (e) => {
            const rect = trackBox.getBoundingClientRect();
            const clickX = e.clientX - rect.left;
            progress = Math.max(0, Math.min(1, clickX / rect.width));
            if (audio.duration) audio.currentTime = progress * audio.duration;
            updateWavyCanvas(progress);
        });
    }

    // Card Play Triggers
    document.querySelectorAll('.card-play-trigger').forEach(btn => {
        btn.addEventListener('click', () => {
            const idx = parseInt(btn.getAttribute('data-song-idx')) || 0;
            songIdx = idx;
            switchSong(songs[songIdx]);
            if (!isPlaying) togglePlay();
        });
    });

    // Next / Prev Triggers
    const pNext = document.getElementById('p-next');
    const pPrev = document.getElementById('p-prev');

    if (pNext) pNext.addEventListener('click', () => {
        songIdx = (songIdx + 1) % songs.length;
        switchSong(songs[songIdx]);
    });

    if (pPrev) pPrev.addEventListener('click', () => {
        songIdx = (songIdx - 1 + songs.length) % songs.length;
        switchSong(songs[songIdx]);
    });

    function switchSong(s) {
        document.getElementById('hero-song-title').textContent = s.title;
        document.getElementById('live-title').textContent = s.title;
        document.getElementById('live-artist').textContent = s.artist;
        document.getElementById('live-cover').src = s.cover;
        document.getElementById('lyric-prev').textContent = s.lyrics[0];
        document.getElementById('lyric-curr').textContent = s.lyrics[1];
        document.getElementById('lyric-next').textContent = s.lyrics[2];

        audio.src = s.src;
        if (isPlaying) audio.play().catch(() => {});
    }

    // 3D Tilt Effect on Player Album
    const tiltCard = document.getElementById('player-album-card');
    if (tiltCard) {
        tiltCard.addEventListener('mousemove', (e) => {
            const rect = tiltCard.getBoundingClientRect();
            const x = e.clientX - rect.left - rect.width / 2;
            const y = e.clientY - rect.top - rect.height / 2;
            tiltCard.style.transform = `perspective(1000px) rotateY(${x / 8}deg) rotateX(${-y / 8}deg) scale(1.04)`;
        });

        tiltCard.addEventListener('mouseleave', () => {
            tiltCard.style.transform = `perspective(1000px) rotateY(0deg) rotateX(0deg) scale(1)`;
        });
    }
}

// -------------------------------------------------------------
// 4. HEADER SCROLL EFFECT
// -------------------------------------------------------------
function initHeaderScroll() {
    window.addEventListener('scroll', () => {
        const header = document.querySelector('.header');
        if (window.scrollY > 40) {
            header.classList.add('scrolled');
        } else {
            header.classList.remove('scrolled');
        }
    });
}
