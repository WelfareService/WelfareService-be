const CODE_LABELS = {
  WELF_HEALTH_001: '만성질환 방문건강관리',
  WELF_MOVE_001: '어르신 이동지원',
  WELF_SENIOR_001: '독거 어르신 돌봄',
  WELF_JOB_001: '시니어 적합 일자리',
  WELF_EDU_001: '디지털 교육 지원'
};

const initialMessages = [
  {
    id: 'bot-intro',
    role: 'bot',
    text: `안녕하세요! 저는 복지 길잡이 민지예요.
무엇이 불편하신지 자연스럽게 말씀해주시면
상황에 맞는 복지 제도를 찾아드릴게요.`
  },
  {
    id: 'bot-tip',
    role: 'bot',
    text: `예시) "혈압 때문에 약값이 부담돼요", "병원이 멀어서 혼자 가기 힘들어요"`
  }
];

const MessageBubble = ({ role, text }) => {
  const timestamp = new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
  return (
    <div className={`bubble ${role}`}>
      {text}
      <div className="meta">
        <span>{role === 'bot' ? '복지 길잡이' : '나'}</span>
        <span>{timestamp}</span>
      </div>
    </div>
  );
};

const ChatInput = ({ value, onChange, onSend, loading }) => (
  <div className="input-bar">
    <input
      placeholder="궁금한 점이나 상황을 말씀해 주세요"
      value={value}
      onChange={e => onChange(e.target.value)}
      onKeyDown={e => {
        if (e.key === 'Enter' && !loading) {
          onSend();
        }
      }}
    />
    <button onClick={onSend} disabled={!value.trim() || loading}>
      {loading ? '분석 중' : '보내기'}
    </button>
  </div>
);

const BottomSheet = ({ locations, mapRef }) => (
  <div className="bottom-sheet">
    <h2>추천 장소 지도</h2>
    <div className="map-container" ref={mapRef}></div>
    {locations.length === 0 ? (
      <p style={{ color: '#8d95a6', fontSize: '0.9rem', margin: 0 }}>
        아직 추천된 시설이 없어요. 원하는 복지 상황을 알려주세요!
      </p>
    ) : (
      <div className="location-list">
        {locations.map((loc, idx) => (
          <div className="location-card" key={`${loc.name}-${idx}`}>
            <span className="tag">{CODE_LABELS[loc.welfareCode] || loc.welfareCode}</span>
            <strong>{loc.name}</strong>
            <div>{loc.address}</div>
          </div>
        ))}
      </div>
    )}
  </div>
);

const App = () => {
  const [messages, setMessages] = React.useState(initialMessages);
  const [input, setInput] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const [locations, setLocations] = React.useState([]);
  const mapContainerRef = React.useRef(null);
  const mapInstanceRef = React.useRef(null);
  const markersRef = React.useRef([]);
  const [kakaoLoaded, setKakaoLoaded] = React.useState(!!(window.kakao && window.kakao.maps));
  const [mapReady, setMapReady] = React.useState(false);
  const endOfMessagesRef = React.useRef(null);

  React.useEffect(() => {
    endOfMessagesRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  React.useEffect(() => {
    if (kakaoLoaded) {
      return;
    }

    if (window.kakao && window.kakao.maps) {
      setKakaoLoaded(true);
      return;
    }

    const script = document.querySelector('script[src*="dapi.kakao.com"]');
    if (!script) {
      return;
    }

    const markAsLoaded = () => {
      script.dataset.loaded = 'true';
      setKakaoLoaded(true);
    };

    if (script.dataset.loaded === 'true' || script.readyState === 'complete') {
      markAsLoaded();
      return;
    }

    script.addEventListener('load', markAsLoaded);
    return () => script.removeEventListener('load', markAsLoaded);
  }, [kakaoLoaded]);

  React.useEffect(() => {
    if (!mapContainerRef.current || !kakaoLoaded) {
      return;
    }
    const { kakao } = window;

    kakao.maps.load(() => {
      if (!mapInstanceRef.current) {
        mapInstanceRef.current = new kakao.maps.Map(mapContainerRef.current, {
          center: new kakao.maps.LatLng(37.5665, 126.9780),
          level: 6
        });
      }
      setMapReady(true);
    });
  }, [mapContainerRef, kakaoLoaded]);

  React.useEffect(() => {
    if (!mapReady || !window.kakao || !mapInstanceRef.current) {
      return;
    }

    const kakao = window.kakao;
    markersRef.current.forEach(marker => marker.setMap(null));
    markersRef.current = [];

    if (locations.length === 0) {
      return;
    }

    const bounds = new kakao.maps.LatLngBounds();
    locations.forEach(loc => {
      const position = new kakao.maps.LatLng(loc.lat, loc.lng);
      const marker = new kakao.maps.Marker({ position });
      marker.setMap(mapInstanceRef.current);
      markersRef.current.push(marker);
      bounds.extend(position);
    });

    mapInstanceRef.current.setBounds(bounds);
  }, [locations, mapReady]);

  const buildBotMessage = (data) => {
    if (!data || data.length === 0) {
      return '아직 매칭된 복지가 없어요. 다른 키워드로 상황을 조금 더 알려주실 수 있을까요?';
    }

    const lines = data.map((item, index) => {
      const name = CODE_LABELS[item.welfareCode] || item.welfareCode;
      return `${index + 1}. ${name} · ${item.score}점`;
    });

    return `말씀해주신 상황과 가장 가까운 제도예요:\n${lines.join('\n')}\n아래 지도를 눌러 가까운 지원기관을 확인해보세요.`;
  };

  const handleSend = async () => {
    if (!input.trim()) {
      return;
    }
    const messageText = input.trim();
    const userMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      text: messageText
    };

    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setLoading(true);

    try {
      const response = await fetch(`/api/recommend?message=${encodeURIComponent(messageText)}`);
      if (!response.ok) {
        throw new Error('추천 API 호출에 실패했습니다.');
      }

      const data = await response.json();
      const mappedLocations = data.flatMap(item =>
        (item.locations || []).map(loc => ({
          ...loc,
          welfareCode: item.welfareCode,
          score: item.score
        }))
      );
      setLocations(mappedLocations);

      const botMessage = {
        id: `bot-${Date.now()}`,
        role: 'bot',
        text: buildBotMessage(data)
      };
      setMessages(prev => [...prev, botMessage]);
    } catch (error) {
      const botMessage = {
        id: `bot-error-${Date.now()}`,
        role: 'bot',
        text: '추천 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.'
      };
      setMessages(prev => [...prev, botMessage]);
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="chat-shell">
      <div className="chat-header">
        <h1>복지 길잡이</h1>
        <p>AI 톤의 챗봇으로 빠르게 복지 혜택을 확인해 보세요.</p>
      </div>

      <div className="messages">
        {messages.map(msg => (
          <MessageBubble key={msg.id} role={msg.role} text={msg.text} />
        ))}
        <div ref={endOfMessagesRef} />
      </div>

      <div className="input-bar-wrapper">
        <ChatInput value={input} onChange={setInput} onSend={handleSend} loading={loading} />
        <BottomSheet locations={locations} mapRef={mapContainerRef} />
      </div>
    </div>
  );
};

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<App />);
