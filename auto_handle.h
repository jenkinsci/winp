#pragma once

// automatically close handle at the end of the scope
class auto_handle {
private:
    HANDLE v;

public:
    auto_handle() {
        v = INVALID_HANDLE_VALUE;
    }

    auto_handle(HANDLE h) {
        v = h;
    }

    ~auto_handle() {
        if (v!=INVALID_HANDLE_VALUE)
            ::CloseHandle(v);
    }

    operator HANDLE& () {
        return v;
    }

	HANDLE* operator & () {
		return &v;
	}
};

// automatically LocalFree at the end of the scope
template <typename T>
class auto_localmem {
private:
    void*   v;

public:
    auto_localmem(void* mem) {
        v = mem;
    }

    ~auto_localmem() {
        if (v!=NULL)
            ::LocalFree(v);
    }

    operator T() {
        return (T)v;
    }
};
