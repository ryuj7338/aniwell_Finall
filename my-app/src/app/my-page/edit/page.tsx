"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";

type Member = {
  id: number;
  regDate: string;
  updateDate: string;
  loginId: string;
  loginPw: string;
  name: string;
  nickname: string;
  cellphone: string;
  email: string;
  delStatus: boolean;
  delDate: string | null;
  authLevel: number;
  authName: string;
  photo: string;
  address: string;

  vetCertUrl: string;
  vetCertApproved: number | null;

  // 소셜 여부 판단용
  socialProvider?: string | null;
  socialId?: string | null;
};

export default function EditPage() {
  const [form, setForm] = useState({
    nickname: "",
    email: "",
    cellphone: "",
    password: "",
    confirmPassword: "",
    name: "",
    address: "",
  });

  const router = useRouter();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [currentStep, setCurrentStep] = useState(1);

  const [user, setUser] = useState<Member | null>(null);
  const [photoPreview, setPhotoPreview] = useState("/img/default-card.png");
  const [pwChangeActive, setPwChangeActive] = useState(false);
  const [pwMatchMsg, setPwMatchMsg] = useState("");
  const [member, setMember] = useState<Member | null>(null);

  // 소셜 여부 파생
  const isSocial =
    !!member?.socialProvider && String(member.socialProvider).trim() !== "";

  useEffect(() => {
    fetch("http://localhost:8080/api/member/getUsrInfo", {
      method: "GET",
      credentials: "include",
    })
      .then((res) => {
        if (!res.ok) throw new Error("회원정보 불러오기 실패");
        return res.json();
      })
      .then((data: Member) => {
        console.log("✅ 불러온 회원정보", data);
        setMember(data);

        // 👉 form 채우기
        setForm({
          nickname: data.nickname || "",
          email: data.email || "",
          cellphone: data.cellphone || "",
          password: "",
          confirmPassword: "",
          name: data.name || "",
          address: data.address || "",
        });

        // 👉 프로필 사진 미리보기
        setPhotoPreview(data.photo ? data.photo : "/img/default-card.png");
      })
      .catch((err) => {
        console.error("❌ 회원 정보 요청 실패", err);
        alert("회원 정보를 불러오는 데 실패했습니다.");
        router.push("/my-page");
      });
  }, []);

  const handleChange = (field: string, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const handlePhotoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => setPhotoPreview(reader.result as string);
      reader.readAsDataURL(file);
    }
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    if (
      !isSocial &&
      pwChangeActive &&
      (form.password.length < 4 || form.password !== form.confirmPassword)
    ) {
      alert("비밀번호 오류");
      return;
    }

    const formData = new FormData();
    formData.append("name", form.name);
    formData.append("nickname", form.nickname);
    formData.append("email", form.email);
    formData.append("cellphone", form.cellphone);
    formData.append("address", form.address);

    // 일반회원이 비번 변경을 선택한 경우에만 비번 전송
    if (!isSocial && pwChangeActive && form.password) {
      formData.append("loginPw", form.password);
    }

    if (fileInputRef.current?.files?.[0]) {
      formData.append("photoFile", fileInputRef.current.files[0]);
    }

    try {
      const res = await fetch("http://localhost:8080/usr/member/doModify", {
        method: "POST",
        body: formData,
        credentials: "include",
      });

      if (res.ok) {
        alert("수정 완료!");
        router.push("/my-page");
      } else {
        alert("서버 오류");
      }
    } catch (err) {
      console.error(err);
      alert("전송 실패");
    }
  };

  useEffect(() => {
    const { password, confirmPassword } = form;

    if (!password || !confirmPassword) {
      setPwMatchMsg("");
      return;
    }

    if (password === confirmPassword) {
      setPwMatchMsg("✅ 비밀번호가 일치합니다.");
    } else {
      setPwMatchMsg("❌ 비밀번호가 일치하지 않습니다.");
    }
  }, [form.password, form.confirmPassword]);

  if (!member) return <div>로딩 중...</div>;

  return (
    <div className="bg-white p-6 rounded-xl shadow-md w-full h-full">
      <form
        onSubmit={handleSubmit}
        encType="multipart/form-data"
        className="grid grid-cols-3 gap-8 relative h-full"
      >
        {/* 🖼 프로필 */}
        <div className="flex flex-col items-center col-span-1 border-r border-gray-300 pr-6">
          <h1 className="text-2xl font-bold mb-6">회원정보 수정</h1>
          <img
            className="w-[120px] h-[120px] object-cover rounded-full border-4 border-gray-200 shadow mb-3"
            src={photoPreview}
            alt="프로필 사진"
          />
          <label
            htmlFor="photoInput"
            className="cursor-pointer text-sm text-gray-600 hover:underline"
          >
            📷 사진 변경하기
          </label>
          <input
            type="file"
            id="photoInput"
            accept="image/*"
            onChange={handlePhotoChange}
            className="hidden"
            ref={fileInputRef}
          />
        </div>

        {/* 📝 기본 정보 */}
        <div className="space-y-5 col-span-2 grid-cols-2">
          {currentStep === 1 && (
            <>
              <div className="flex items-center gap-4">
                <div className="w-[30%] font-semibold text-gray-700">
                  아이디
                </div>
                <div className="w-[80%] p-2 bg-gray-100 rounded-md shadow-inner text-sm">
                  {member.loginId}
                </div>
              </div>

              {/* 소셜이면 비밀번호 섹션 자체를 숨김 */}
              {!isSocial && (
                <>
                  <div className="flex items-center gap-4">
                    <div className="w-[30%] font-semibold text-gray-700">
                      비밀번호
                    </div>
                    <button
                      type="button"
                      className="btn btn-outline btn-sm w-[80%]"
                      onClick={() => setPwChangeActive(!pwChangeActive)}
                    >
                      {pwChangeActive ? "비밀번호 변경 취소" : "비밀번호 변경"}
                    </button>
                  </div>

                  {pwChangeActive && (
                    <>
                      <div className="flex items-center gap-4">
                        <div className="w-[30%] font-semibold text-gray-700">
                          새 비밀번호
                        </div>
                        <input
                          type="password"
                          value={form.password}
                          onChange={(e) => handleChange("password", e.target.value)}
                          className="p-2 input input-sm w-[80%] shadow rounded-md border"
                        />
                      </div>
                      <div className="flex items-center gap-4">
                        <div className="w-[30%] font-semibold text-gray-700">
                          비밀번호 확인
                        </div>
                        <input
                          type="password"
                          value={form.confirmPassword}
                          onChange={(e) =>
                            handleChange("confirmPassword", e.target.value)
                          }
                          className="p-2 input input-sm w-[80%] shadow rounded-md border"
                        />
                      </div>
                      <div className="text-sm text-gray-600 pl-[30%]">
                        {pwMatchMsg}
                      </div>
                    </>
                  )}
                </>
              )}

              <div className="flex justify-end mt-4">
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={() => setCurrentStep(2)}
                >
                  다음 →
                </button>
              </div>
            </>
          )}

          {currentStep === 2 && (
            <>
              <div className="grid grid-cols-2 gap-6">
                {/* 닉네임 */}
                <div className="flex items-center gap-4">
                  <div className="w-[30%] font-semibold text-gray-700">
                    닉네임
                  </div>
                  <input
                    type="text"
                    value={form.nickname}
                    onChange={(e) => handleChange("nickname", e.target.value)}
                    className="p-2 input input-sm w-full shadow rounded-md border"
                  />
                </div>

                {/* 이메일 */}
                <div className="flex items-center gap-4">
                  <div className="w-[30%] font-semibold text-gray-700">
                    이메일
                  </div>
                  <input
                    type="email"
                    value={form.email}
                    onChange={(e) => handleChange("email", e.target.value)}
                    className="p-2 input input-sm w-full shadow rounded-md border"
                  />
                </div>

                {/* 전화번호 */}
                <div className="flex items-center gap-4">
                  <div className="w-[30%] font-semibold text-gray-700">
                    전화번호
                  </div>
                  <input
                    type="text"
                    value={form.cellphone}
                    onChange={(e) => handleChange("cellphone", e.target.value)}
                    className="p-2 input input-sm w-full shadow rounded-md border"
                  />
                </div>

                {/* 주소 */}
                <div className="flex items-center gap-4">
                  <div className="w-[30%] font-semibold text-gray-700">
                    주소
                  </div>
                  <input
                    type="text"
                    value={form.address}
                    onChange={(e) => handleChange("address", e.target.value)}
                    className="p-2 input input-sm w-full shadow rounded-md border"
                  />
                </div>
              </div>

              {/* 버튼 */}
              <div className="flex justify-between mt-6">
                <button
                  type="button"
                  className="btn"
                  onClick={() => setCurrentStep(1)}
                >
                  ← 이전
                </button>
                <button type="submit" className="btn btn-primary">
                  수정하기
                </button>
              </div>
            </>
          )}
        </div>
      </form>
    </div>
  );
}
